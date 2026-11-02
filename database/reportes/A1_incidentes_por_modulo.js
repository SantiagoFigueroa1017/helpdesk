// =====================================================================
// A1 · Incidentes y tiempo promedio de resolución por módulo del ERP
// Historia: HU22 (consulta de tickets) · Etapas: $match, $group, $project, $sort
// Colección: tickets · Índice: ix_tickets_modulo_tipo_fecha
//
// Responde la pregunta del enunciado: qué módulo del ERP falla más.
// Cómo se usa: pegar completo en la consola mongosh de Compass (Atlas o
// local). No modifica datos. Debe terminar en RESULTADO: OK.
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
  // ---- Parámetros (en la API llegan como desde y hasta) ---------------
  // Por defecto, toda la historia de la semilla: 2025-11-01 al corte.
  const desde = ISODate("2025-11-01T00:00:00Z");
  const hasta = ISODate("2026-11-01T12:00:00Z");

  // El índice empieza por "modulo". Sin un filtro por módulo el
  // planificador no puede usarlo y recorre la colección. Con los 5 módulos
  // en $in, el índice entra con 5 rangos y deja fuera las sugerencias.
  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];
  const ABIERTOS = ["ABIERTO", "EN_PROCESO"];
  const CERRADOS = ["RESUELTO", "CERRADO"];

  const pipeline = [
    // 1. Incidentes del periodo.
    { $match: { modulo: { $in: MODULOS }, tipo: "INCIDENTE", fechaCreacion: { $gte: desde, $lt: hasta } } },
    // 2. Un grupo por módulo. El tiempo de resolución solo cuenta los
    //    tickets con fechaResolucion; $avg ignora los null.
    {
      $group: {
        _id: "$modulo",
        incidentes: { $sum: 1 },
        abiertos: { $sum: { $cond: [{ $in: ["$estado", ABIERTOS] }, 1, 0] } },
        cerrados: { $sum: { $cond: [{ $in: ["$estado", CERRADOS] }, 1, 0] } },
        msPromedio: {
          $avg: {
            $cond: [
              { $ne: [{ $ifNull: ["$fechaResolucion", null] }, null] },
              { $subtract: ["$fechaResolucion", "$fechaCreacion"] },
              null
            ]
          }
        }
      }
    },
    // 3. Horas con un decimal y porcentaje de cerrados.
    {
      $project: {
        _id: 0,
        modulo: "$_id",
        incidentes: 1,
        abiertos: 1,
        cerrados: 1,
        horasPromedioResolucion: { $round: [{ $divide: ["$msPromedio", 3600000] }, 1] },
        porcentajeCerrados: { $round: [{ $multiply: [{ $divide: ["$cerrados", "$incidentes"] }, 100] }, 1] }
      }
    },
    // 4. El módulo que más falla primero.
    { $sort: { incidentes: -1, modulo: 1 } }
  ];

  print("\n=== A1 · Incidentes por módulo del ERP ===");
  print("Periodo: " + desde.toISOString() + " a " + hasta.toISOString() + "\n");
  const filas = base.tickets.aggregate(pipeline).toArray();

  print(izq("Módulo", 18) + der("Incid.", 8) + der("Abiertos", 10) + der("Cerrados", 10) +
        der("% cerr.", 9) + der("Horas prom.", 13));
  print("-".repeat(68));
  filas.forEach(f => print(izq(f.modulo, 18) + der(f.incidentes, 8) + der(f.abiertos, 10) +
    der(f.cerrados, 10) + der(f.porcentajeCerrados, 9) + der(nulo(f.horasPromedioResolucion), 13)));

  print("\nComprobaciones:");
  const total = filas.reduce((s, f) => s + f.incidentes, 0);
  const esperado = base.tickets.countDocuments({ tipo: "INCIDENTE", fechaCreacion: { $gte: desde, $lt: hasta } });
  check("Suma de incidentes = countDocuments", total === esperado, total + " / " + esperado);
  check("Abiertos + cerrados = incidentes en cada módulo", filas.every(f => f.abiertos + f.cerrados === f.incidentes));
  check("Orden descendente por incidentes", filas.every((f, i) => i === 0 || filas[i - 1].incidentes >= f.incidentes));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.tickets.explain("executionStats").aggregate(pipeline));
  const sin = resumirExplain(base.tickets.explain("executionStats").aggregate(pipeline, { hint: { $natural: 1 } }));
  mostrarExplain("Con índice", con);
  mostrarExplain("Sin índice", sin);
  check("Usa ix_tickets_modulo_tipo_fecha", usaIndice(con, "ix_tickets_modulo_tipo_fecha"));
  check("Sin índice recorre la colección", sin.collscan);

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
