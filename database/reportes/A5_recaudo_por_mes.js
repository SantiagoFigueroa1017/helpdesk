// =====================================================================
// A5 · Recaudo y pagos fallidos por mes
// Historia: HU18 (consulta de pagos) · Etapas: $match, $group, $sort
// Colección: pagos · Índice: ix_pagos_resultado_fecha
//                    (ix_pagos_empresa_fecha si se filtra por empresa)
//
// Los meses se cuentan en hora de Colombia (America/Bogota). El dinero es
// Decimal128 y se suma como Decimal128, sin pasar por double.
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
  // ---- Parámetros ---------------------------------------------------
  // De noviembre de 2025 a octubre de 2026, medianoche de Bogotá (UTC-5).
  const desde = ISODate("2025-11-01T05:00:00Z");
  const hasta = ISODate("2026-11-01T05:00:00Z");
  // CLIENTE_ADMIN: el backend pone aquí el empresaId del usuario. null = todas.
  const empresaId = null;
  const ZONA = "America/Bogota";

  const filtro = empresaId
    ? { empresaId: empresaId, fechaPago: { $gte: desde, $lt: hasta } }
    // resultado con los dos valores: así la consulta entra por
    // ix_pagos_resultado_fecha con 2 rangos de fecha.
    : { resultado: { $in: ["APROBADO", "FALLIDO"] }, fechaPago: { $gte: desde, $lt: hasta } };

  const pipeline = [
    // 1. Pagos del periodo.
    { $match: filtro },
    // 2. Un grupo por mes de Bogotá.
    {
      $group: {
        _id: { $dateToString: { format: "%Y-%m", date: "$fechaPago", timezone: ZONA } },
        recaudo: { $sum: { $cond: [{ $eq: ["$resultado", "APROBADO"] }, "$monto", NumberDecimal("0")] } },
        aprobados: { $sum: { $cond: [{ $eq: ["$resultado", "APROBADO"] }, 1, 0] } },
        fallidos: { $sum: { $cond: [{ $eq: ["$resultado", "FALLIDO"] }, 1, 0] } },
        montoFallido: { $sum: { $cond: [{ $eq: ["$resultado", "FALLIDO"] }, "$monto", NumberDecimal("0")] } }
      }
    },
    // 3. Orden cronológico.
    { $sort: { _id: 1 } }
  ];

  print("\n=== A5 · Recaudo y pagos fallidos por mes ===");
  print("Periodo: " + desde.toISOString() + " a " + hasta.toISOString() + " · zona " + ZONA + "\n");
  const filas = base.pagos.aggregate(pipeline).toArray();

  print(izq("Mes", 9) + der("Recaudo", 16) + der("Aprob.", 8) + der("Fallidos", 10) + der("% fallo", 9) + der("Monto fallido", 16));
  print("-".repeat(68));
  filas.forEach(f => {
    const pct = Math.round(f.fallidos / (f.aprobados + f.fallidos) * 1000) / 10;
    print(izq(f._id, 9) + der(pesos(f.recaudo), 16) + der(f.aprobados, 8) + der(f.fallidos, 10) +
          der(pct, 9) + der(pesos(f.montoFallido), 16));
  });
  const recaudoTotal = filas.reduce((s, f) => s + Number(f.recaudo.toString()), 0);
  print("-".repeat(68));
  print(izq("Total", 9) + der(pesos(recaudoTotal), 16) + der(filas.reduce((s, f) => s + f.aprobados, 0), 8) +
        der(filas.reduce((s, f) => s + f.fallidos, 0), 10));

  print("\nComprobaciones:");
  const pagos = filas.reduce((s, f) => s + f.aprobados + f.fallidos, 0);
  const esperado = base.pagos.countDocuments({ fechaPago: { $gte: desde, $lt: hasta } });
  check("Pagos contados = countDocuments del periodo", pagos === esperado, pagos + " / " + esperado);
  const fallidos = filas.reduce((s, f) => s + f.fallidos, 0);
  const fallidosBD = base.pagos.countDocuments({ resultado: "FALLIDO", fechaPago: { $gte: desde, $lt: hasta } });
  check("Fallidos contados = countDocuments FALLIDO", fallidos === fallidosBD, fallidos + " / " + fallidosBD);
  check("El recaudo sale como Decimal128", filas.every(f => f.recaudo && f.recaudo.constructor && /Decimal/.test(f.recaudo.constructor.name)),
        filas.length ? filas[0].recaudo.constructor.name : "-");
  check("Orden cronológico", filas.every((f, i) => i === 0 || filas[i - 1]._id < f._id));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.pagos.explain("executionStats").aggregate(pipeline));
  const sin = resumirExplain(base.pagos.explain("executionStats").aggregate(pipeline, { hint: { $natural: 1 } }));
  mostrarExplain("Con índice", con);
  mostrarExplain("Sin índice", sin);
  const indice = empresaId ? "ix_pagos_empresa_fecha" : "ix_pagos_resultado_fecha";
  check("Usa " + indice, usaIndice(con, indice));
  check("Sin índice recorre la colección", sin.collscan);

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
