// =====================================================================
// A6 · Porcentaje de asistencia por capacitación
// Historia: HU34 (consulta de capacitaciones) · Etapas: $lookup, $project, $sort
// Colección: capacitaciones · Índice: ux_inscripciones_capacitacion_usuario
//
// Porcentaje = asistieron / asistencias marcadas. Las inscripciones sin
// marcar (asistio: null) no cuentan como ausencia. Las capacitaciones
// futuras o canceladas salen sin porcentaje.
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
  const ahora = new Date();

  const pipeline = [
    // 1. Inscripciones de la capacitación. capacitacionId es la primera
    //    llave del índice único ux_inscripciones_capacitacion_usuario.
    {
      $lookup: {
        from: "inscripciones",
        localField: "_id",
        foreignField: "capacitacionId",
        pipeline: [{ $project: { _id: 0, asistio: 1 } }],
        as: "inscripciones"
      }
    },
    // 2. Conteos y porcentaje. "Realizada" se deriva de fechaFin (1.2).
    {
      $project: {
        _id: 0,
        titulo: 1,
        tipo: 1,
        modulo: 1,
        fechaInicio: 1,
        estado: 1,
        cupo: 1,
        cuposOcupados: 1,
        realizada: { $and: [{ $eq: ["$estado", "PROGRAMADA"] }, { $lt: ["$fechaFin", ahora] }] },
        inscritos: { $size: "$inscripciones" },
        asistieron: { $size: { $filter: { input: "$inscripciones", cond: { $eq: ["$$this.asistio", true] } } } },
        marcadas: { $size: { $filter: { input: "$inscripciones", cond: { $ne: ["$$this.asistio", null] } } } }
      }
    },
    {
      $project: {
        titulo: 1, tipo: 1, modulo: 1, fechaInicio: 1, estado: 1, cupo: 1, cuposOcupados: 1,
        realizada: 1, inscritos: 1, asistieron: 1, marcadas: 1,
        porcentajeAsistencia: {
          $cond: [
            { $gt: ["$marcadas", 0] },
            { $round: [{ $multiply: [{ $divide: ["$asistieron", "$marcadas"] }, 100] }, 1] },
            null
          ]
        }
      }
    },
    // 3. Cronograma: las más recientes primero.
    { $sort: { fechaInicio: -1 } }
  ];

  print("\n=== A6 · Porcentaje de asistencia por capacitación ===\n");
  const filas = base.capacitaciones.aggregate(pipeline).toArray();

  print(izq("Fecha", 11) + izq("Tipo", 7) + izq("Módulo", 16) + izq("Estado", 11) +
        der("Cupo", 5) + der("Insc.", 6) + der("Asist.", 7) + der("Marc.", 6) + der("%", 7) + "  Título");
  print("-".repeat(112));
  filas.forEach(f => print(izq(dia(f.fechaInicio), 11) + izq(f.tipo === "MASIVA" ? "MAS" : "PERS", 7) + izq(f.modulo, 16) +
    izq(f.estado === "CANCELADA" ? "CANCELADA" : f.realizada ? "REALIZADA" : "PROGRAMADA", 11) +
    der(f.cupo, 5) + der(f.inscritos, 6) + der(f.asistieron, 7) + der(f.marcadas, 6) +
    der(nulo(f.porcentajeAsistencia), 7) + "  " + f.titulo.slice(0, 45)));

  const realizadas = filas.filter(f => f.porcentajeAsistencia !== null);
  const asist = realizadas.reduce((s, f) => s + f.asistieron, 0);
  const marc = realizadas.reduce((s, f) => s + f.marcadas, 0);
  print("\nAsistencia global en las realizadas: " + (marc ? Math.round(asist / marc * 1000) / 10 : "-") +
        " % (" + asist + " de " + marc + " marcadas)");

  print("\nComprobaciones:");
  check("Una fila por capacitación", filas.length === base.capacitaciones.countDocuments(), filas.length);
  const inscritos = filas.reduce((s, f) => s + f.inscritos, 0);
  check("Suma de inscritos = inscripciones", inscritos === base.inscripciones.countDocuments(), inscritos + " / " + base.inscripciones.countDocuments());
  check("cuposOcupados = inscritos en cada capacitación", filas.every(f => f.cuposOcupados === f.inscritos));
  check("Ninguna futura tiene asistencia marcada", filas.every(f => f.fechaInicio < ahora || f.marcadas === 0));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.capacitaciones.explain("executionStats").aggregate(pipeline));
  mostrarExplain("Pipeline", con);
  check("El $lookup a inscripciones usa ux_inscripciones_capacitacion_usuario",
        lookupUsa(con, "inscripciones", "ux_inscripciones_capacitacion_usuario"));
  print("  Nota: se listan todas las capacitaciones; leer las " + base.capacitaciones.countDocuments() + " es lo esperado.");

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
