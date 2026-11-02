// =====================================================================
// A2 · Empresas con membresía activa por plan
// Historia: HU10 (consulta de planes) · Etapas: $lookup, $project, $sort
// Colección: planes · Índices: ix_membresias_plan_estado y _id_ de empresas
//
// Para cada plan muestra cuántas empresas lo tienen con la membresía
// ACTIVA y cuáles de ellas ya pasaron la fecha de vencimiento (sin
// soporte, contexto 7.5). Incluye los planes inactivos con 0 empresas.
// Cómo se usa: pegar completo en la consola mongosh de Compass. No
// modifica datos. Debe terminar en RESULTADO: OK.
// =====================================================================

(() => {
  const base = db.getSiblingDB("helpdesk");
  // ---- Utilidades comunes (el mismo bloque en A1 a A9) -----------------
  const izq = (t, n) => String(t).padEnd(n);
  const der = (t, n) => String(t).padStart(n);
  const dia = f => (f ? f.toISOString().slice(0, 10) : "-");
  const miles = n => Math.round(Number(n)).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const pesos = d => (d === null || d === undefined ? "-" : "$" + miles(Number(d.toString())));
  const nulo = v => (v === null || v === undefined ? "-" : v);

  let fallas = 0;
  const check = (nombre, ok, detalle) => {
    print((ok ? "  OK    " : "  FALLA ") + nombre + (detalle !== undefined ? "  (" + detalle + ")" : ""));
    if (!ok) fallas++;
  };

  // Resume un explain("executionStats") de aggregate. Lee los dos formatos:
  //  - clásico: stages[0].$cursor tiene el plan de la colección principal y
  //    cada etapa $lookup trae indexesUsed, collectionScans y totalDocsExamined;
  //  - SBE (motor nuevo): el plan completo está en queryPlanner.winningPlan y
  //    cada $lookup aparece como una etapa EQ_LOOKUP con su estrategia.
  const resumirExplain = exp => {
    const r = { indices: new Set(), collscan: false, llaves: null, docs: null, ms: null, lookups: [] };
    const fuente = Array.isArray(exp.stages) && exp.stages[0] && exp.stages[0].$cursor ? exp.stages[0].$cursor : exp;
    const visitar = o => {
      if (o === null || typeof o !== "object") return;
      if (Array.isArray(o)) { o.forEach(visitar); return; }
      if (o.stage === "EQ_LOOKUP") {
        r.lookups.push({
          from: String(o.foreignCollection || "").split(".").pop(),
          indices: o.indexName ? [o.indexName] : [],
          scans: o.strategy === "IndexedLoopJoin" ? 0 : 1,
          docs: null
        });
        visitar(o.inputStage);            // el lado local sigue siendo el plan principal
        return;
      }
      if (o.stage === "COLLSCAN") r.collscan = true;
      if (typeof o.indexName === "string") r.indices.add(o.indexName);
      for (const k of Object.keys(o)) if (k !== "rejectedPlans") visitar(o[k]);
    };
    visitar(fuente.queryPlanner && fuente.queryPlanner.winningPlan);
    const st = fuente.executionStats || exp.executionStats || {};
    if (st.totalKeysExamined !== undefined) r.llaves = Number(st.totalKeysExamined);
    if (st.totalDocsExamined !== undefined) r.docs = Number(st.totalDocsExamined);
    if (st.executionTimeMillis !== undefined) r.ms = Number(st.executionTimeMillis);
    if (Array.isArray(exp.stages)) {
      for (const s of exp.stages) {
        if (!s.$lookup) continue;
        r.lookups.push({
          from: s.$lookup.from,
          indices: (s.indexesUsed || []).map(String),
          scans: Number(s.collectionScans || 0),
          docs: s.totalDocsExamined !== undefined ? Number(s.totalDocsExamined) : null
        });
      }
    }
    return r;
  };
  const mostrarExplain = (titulo, r) => {
    const plan = r.collscan ? "COLLSCAN" : ([...r.indices].join(", ") || "-");
    print("  " + izq(titulo, 12) + "plan: " + izq(plan, 38) +
          " llaves: " + der(nulo(r.llaves), 6) + "  docs: " + der(nulo(r.docs), 6) + "  ms: " + der(nulo(r.ms), 4));
    for (const l of r.lookups) {
      print("      $lookup a " + izq(l.from, 24) + "índice: " + izq(l.indices.join(", ") || "ninguno", 38) +
            (l.scans ? "  COLLSCAN x" + l.scans : "") + (l.docs !== null ? "  docs: " + l.docs : ""));
    }
  };
  const usaIndice = (r, nombre) => r.indices.has(nombre) && !r.collscan;
  const lookupUsa = (r, from, nombre) => r.lookups.some(l => l.from === from && l.indices.includes(nombre) && l.scans === 0);
  // ---------------------------------------------------------------------
  // ---- Parámetro: fecha con la que se calcula el vencimiento ----------
  const ahora = new Date();

  const pipeline = [
    // 1. Membresías ACTIVA del plan, con el nombre de la empresa.
    //    localField + foreignField + pipeline (MongoDB 5.0 o superior): la
    //    igualdad planId usa ix_membresias_plan_estado y el $match de
    //    adentro agrega estado como segunda llave del mismo índice.
    {
      $lookup: {
        from: "membresias",
        localField: "_id",
        foreignField: "planId",
        pipeline: [
          { $match: { estado: "ACTIVA" } },
          {
            $lookup: {
              from: "empresas",
              localField: "empresaId",
              foreignField: "_id",
              pipeline: [{ $project: { _id: 0, razonSocial: 1 } }],
              as: "empresa"
            }
          },
          {
            $project: {
              _id: 0,
              razonSocial: { $first: "$empresa.razonSocial" },
              fechaVencimiento: 1,
              vigente: { $lte: [ahora, "$fechaVencimiento"] }
            }
          }
        ],
        as: "activas"
      }
    },
    // 2. Conteos y lista de empresas ordenada por nombre.
    {
      $project: {
        _id: 0,
        plan: "$nombre",
        nivelAcceso: 1,
        precio: 1,
        activo: 1,
        empresasActivas: { $size: "$activas" },
        vigentes: { $size: { $filter: { input: "$activas", cond: "$$this.vigente" } } },
        vencidas: { $size: { $filter: { input: "$activas", cond: { $not: ["$$this.vigente"] } } } },
        empresas: { $sortArray: { input: "$activas.razonSocial", sortBy: 1 } }
      }
    },
    // 3. El plan con más empresas primero.
    { $sort: { empresasActivas: -1, plan: 1 } }
  ];

  print("\n=== A2 · Empresas con membresía activa por plan ===");
  print("Vencimiento calculado al " + ahora.toISOString() + "\n");
  const filas = base.planes.aggregate(pipeline).toArray();

  print(izq("Plan", 14) + izq("Nivel", 10) + der("Precio", 12) + izq("  Estado", 11) +
        der("Activas", 9) + der("Vigentes", 10) + der("Vencidas", 10));
  print("-".repeat(76));
  filas.forEach(f => print(izq(f.plan, 14) + izq(f.nivelAcceso, 10) + der(pesos(f.precio), 12) +
    izq(f.activo ? "  activo" : "  inactivo", 11) + der(f.empresasActivas, 9) + der(f.vigentes, 10) + der(f.vencidas, 10)));
  filas.filter(f => f.empresas.length).forEach(f =>
    print("\n" + f.plan + ": " + f.empresas.join(", ")));

  print("\nComprobaciones:");
  const total = filas.reduce((s, f) => s + f.empresasActivas, 0);
  const esperado = base.membresias.countDocuments({ estado: "ACTIVA" });
  check("Suma de empresas activas = membresías ACTIVA", total === esperado, total + " / " + esperado);
  const vencidas = filas.reduce((s, f) => s + f.vencidas, 0);
  const vencidasBD = base.membresias.countDocuments({ estado: "ACTIVA", fechaVencimiento: { $lt: ahora } });
  check("Suma de vencidas = ACTIVA con fechaVencimiento pasada", vencidas === vencidasBD, vencidas + " / " + vencidasBD);
  check("Aparecen todos los planes, también los inactivos", filas.length === base.planes.countDocuments(), filas.length);
  check("Cada empresa tiene nombre", filas.every(f => f.empresas.length === f.empresasActivas));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.planes.explain("executionStats").aggregate(pipeline));
  mostrarExplain("Pipeline", con);
  check("El $lookup a membresias usa ix_membresias_plan_estado", lookupUsa(con, "membresias", "ix_membresias_plan_estado"));
  print("  Nota: planes tiene " + base.planes.countDocuments() + " documentos; leerlos todos es lo esperado.");

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
