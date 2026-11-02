// =====================================================================
// A4 · Plan contratado y tickets por empresa
// Historia: HU02 (consulta de empresas) · Etapas: $lookup, $group, $project, $sort
// Colección: empresas · Índices: ux_membresias_empresa y ix_tickets_empresa_estado_fecha
//
// Una fila por empresa, también las que no tienen membresía ni tickets.
// El $group corre dentro del $lookup a tickets: MongoDB cuenta los tickets
// de cada empresa y devuelve un solo documento con los totales, en vez de
// traer miles de tickets al pipeline principal.
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
  const ABIERTOS = ["ABIERTO", "EN_PROCESO"];
  const ahora = new Date();

  const pipeline = [
    // 1. Membresía de la empresa con el nombre de su plan.
    {
      $lookup: {
        from: "membresias",
        localField: "_id",
        foreignField: "empresaId",
        pipeline: [
          {
            $lookup: {
              from: "planes",
              localField: "planId",
              foreignField: "_id",
              pipeline: [{ $project: { _id: 0, nombre: 1, nivelAcceso: 1 } }],
              as: "plan"
            }
          },
          { $project: { _id: 0, estado: 1, fechaVencimiento: 1, modulosContratados: 1, plan: { $first: "$plan" } } }
        ],
        as: "membresia"
      }
    },
    // 2. Totales de tickets de la empresa, calculados por MongoDB.
    {
      $lookup: {
        from: "tickets",
        localField: "_id",
        foreignField: "empresaId",
        pipeline: [
          {
            $group: {
              _id: null,
              total: { $sum: 1 },
              abiertos: { $sum: { $cond: [{ $in: ["$estado", ABIERTOS] }, 1, 0] } },
              incidentes: { $sum: { $cond: [{ $eq: ["$tipo", "INCIDENTE"] }, 1, 0] } },
              sugerencias: { $sum: { $cond: [{ $eq: ["$tipo", "SUGERENCIA"] }, 1, 0] } }
            }
          }
        ],
        as: "tickets"
      }
    },
    // 3. Una fila plana. "VENCIDA" se deriva: no es un estado guardado (7.5).
    {
      $project: {
        _id: 0,
        nit: 1,
        razonSocial: 1,
        plan: { $ifNull: [{ $first: "$membresia.plan.nombre" }, "Sin membresía"] },
        estadoMembresia: {
          $let: {
            vars: { m: { $first: "$membresia" } },
            in: {
              $cond: [
                { $eq: [{ $ifNull: ["$$m", null] }, null] }, "-",
                { $cond: [{ $and: [{ $eq: ["$$m.estado", "ACTIVA"] }, { $lt: ["$$m.fechaVencimiento", ahora] }] }, "VENCIDA", "$$m.estado"] }
              ]
            }
          }
        },
        modulos: { $size: { $ifNull: [{ $first: "$membresia.modulosContratados" }, []] } },
        tickets: { $ifNull: [{ $first: "$tickets.total" }, 0] },
        abiertos: { $ifNull: [{ $first: "$tickets.abiertos" }, 0] },
        incidentes: { $ifNull: [{ $first: "$tickets.incidentes" }, 0] },
        sugerencias: { $ifNull: [{ $first: "$tickets.sugerencias" }, 0] }
      }
    },
    // 4. Las empresas con más tickets primero.
    { $sort: { tickets: -1, razonSocial: 1 } }
  ];

  print("\n=== A4 · Plan contratado y tickets por empresa ===\n");
  const filas = base.empresas.aggregate(pipeline).toArray();

  print(izq("Empresa", 40) + izq("Plan", 14) + izq("Membresía", 12) + der("Mód.", 5) +
        der("Tickets", 9) + der("Abiertos", 9) + der("Incid.", 8) + der("Sug.", 6));
  print("-".repeat(103));
  filas.forEach(f => print(izq(f.razonSocial.slice(0, 39), 40) + izq(f.plan, 14) + izq(f.estadoMembresia, 12) +
    der(f.modulos, 5) + der(f.tickets, 9) + der(f.abiertos, 9) + der(f.incidentes, 8) + der(f.sugerencias, 6)));

  print("\nComprobaciones:");
  check("Una fila por empresa", filas.length === base.empresas.countDocuments(), filas.length);
  const total = filas.reduce((s, f) => s + f.tickets, 0);
  const esperado = base.tickets.countDocuments();
  check("Suma de tickets = total de la colección", total === esperado, total + " / " + esperado);
  check("Incidentes + sugerencias = tickets en cada empresa", filas.every(f => f.incidentes + f.sugerencias === f.tickets));
  const sinMem = filas.filter(f => f.plan === "Sin membresía").length;
  check("Empresas sin membresía = empresas - membresías", sinMem === base.empresas.countDocuments() - base.membresias.countDocuments(), sinMem);

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.empresas.explain("executionStats").aggregate(pipeline));
  mostrarExplain("Pipeline", con);
  check("El $lookup a membresias usa ux_membresias_empresa", lookupUsa(con, "membresias", "ux_membresias_empresa"));
  check("El $lookup a tickets usa ix_tickets_empresa_estado_fecha", lookupUsa(con, "tickets", "ix_tickets_empresa_estado_fecha"));
  print("  Nota: se listan todas las empresas; leer las " + base.empresas.countDocuments() + " es lo esperado.");

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
