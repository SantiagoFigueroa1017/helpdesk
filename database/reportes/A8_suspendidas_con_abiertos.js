// =====================================================================
// A8 · Empresas suspendidas con tickets abiertos
// Historia: HU14 (consulta de membresías) · Etapas: $match, $lookup, $project
// Colección: membresias · Índices: _id_ de empresas, _id_ de planes e
//                                  ix_tickets_empresa_estado_fecha
//
// Muestra el efecto de la regla "sin pago no hay soporte": las empresas
// con la membresía SUSPENDIDA cuyos tickets quedaron abiertos sin que
// nadie los atienda. Sirve para llamar al contacto y cobrar.
// El orden de salida lo pone quien consume el reporte (el script ordena
// al imprimir); el documento fija para A8 las etapas $match, $lookup y
// $project.
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

  const pipeline = [
    // 1. Membresías suspendidas. membresias tiene una por empresa (38 en
    //    la semilla); ningún índice empieza por estado y no hace falta.
    { $match: { estado: "SUSPENDIDA" } },
    // 2. Empresa, plan y tickets abiertos.
    {
      $lookup: {
        from: "empresas",
        localField: "empresaId",
        foreignField: "_id",
        pipeline: [{ $project: { _id: 0, nit: 1, razonSocial: 1, contacto: 1 } }],
        as: "empresa"
      }
    },
    {
      $lookup: {
        from: "planes",
        localField: "planId",
        foreignField: "_id",
        pipeline: [{ $project: { _id: 0, nombre: 1 } }],
        as: "plan"
      }
    },
    {
      $lookup: {
        from: "tickets",
        localField: "empresaId",
        foreignField: "empresaId",
        pipeline: [
          { $match: { estado: { $in: ABIERTOS } } },
          { $project: { _id: 0, titulo: 1, modulo: 1, estado: 1, fechaCreacion: 1 } }
        ],
        as: "abiertos"
      }
    },
    // 3. Fila plana. fechaActualizacion de la membresía = cuándo la
    //    suspendió la transacción del pago fallido (HU17).
    {
      $project: {
        _id: 0,
        empresa: { $first: "$empresa.razonSocial" },
        nit: { $first: "$empresa.nit" },
        contacto: { $first: "$empresa.contacto.nombre" },
        telefono: { $first: "$empresa.contacto.telefono" },
        email: { $first: "$empresa.contacto.email" },
        plan: { $first: "$plan.nombre" },
        suspendidaDesde: "$fechaActualizacion",
        ticketsAbiertos: { $size: "$abiertos" },
        abiertos: "$abiertos"
      }
    },
    // 4. Solo las que tienen tickets abiertos.
    { $match: { ticketsAbiertos: { $gt: 0 } } }
  ];

  print("\n=== A8 · Empresas suspendidas con tickets abiertos ===\n");
  const filas = base.membresias.aggregate(pipeline).toArray()
    .sort((a, b) => b.ticketsAbiertos - a.ticketsAbiertos || (a.empresa < b.empresa ? -1 : 1));

  filas.forEach(f => {
    print(f.empresa + " · NIT " + f.nit + " · plan " + f.plan + " · suspendida desde " + dia(f.suspendidaDesde));
    print("  Contacto: " + f.contacto + " · " + f.telefono + " · " + f.email);
    print("  Tickets abiertos: " + f.ticketsAbiertos);
    f.abiertos
      .sort((a, b) => a.fechaCreacion - b.fechaCreacion)
      .forEach(t => print("    " + dia(t.fechaCreacion) + "  " + izq(t.estado, 11) + izq(t.modulo, 16) + t.titulo));
    print("");
  });
  const suspendidas = base.membresias.countDocuments({ estado: "SUSPENDIDA" });
  print(filas.length + " de " + suspendidas + " empresas suspendidas tienen tickets abiertos.");

  print("\nComprobaciones:");
  // Contado de otra forma: tickets abiertos de cada empresa suspendida.
  const conteo = base.membresias.find({ estado: "SUSPENDIDA" }, { empresaId: 1 }).toArray()
    .map(m => base.tickets.countDocuments({ empresaId: m.empresaId, estado: { $in: ABIERTOS } }));
  check("Empresas con abiertos coincide con countDocuments", filas.length === conteo.filter(n => n > 0).length,
        filas.length + " / " + conteo.filter(n => n > 0).length);
  check("Total de abiertos coincide", filas.reduce((s, f) => s + f.ticketsAbiertos, 0) === conteo.reduce((s, n) => s + n, 0),
        filas.reduce((s, f) => s + f.ticketsAbiertos, 0));
  check("Cada fila tiene empresa y plan", filas.every(f => f.empresa && f.plan));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.membresias.explain("executionStats").aggregate(pipeline));
  mostrarExplain("Pipeline", con);
  check("El $lookup a tickets usa ix_tickets_empresa_estado_fecha", lookupUsa(con, "tickets", "ix_tickets_empresa_estado_fecha"));
  check("El $lookup a empresas usa _id_", lookupUsa(con, "empresas", "_id_"));

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
