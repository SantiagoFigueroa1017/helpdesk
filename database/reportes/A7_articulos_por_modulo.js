// =====================================================================
// A7 · Artículos y tickets resueltos por módulo
// Historia: HU30 (base de conocimientos) · Etapas: $lookup, $group, $project
// Colección: articulos · Índice: _id_ de tickets (el $lookup recorre ticketIds)
//
// Mide cuánto se usa la base de conocimientos: por módulo, cuántos
// artículos hay y cuántos tickets distintos se resolvieron con ellos.
// Un ticket vinculado a dos artículos del mismo módulo cuenta una vez.
// El orden de salida lo pone quien consume el reporte (el script ordena
// por módulo al imprimir); el documento de requisitos fija para A7 las
// etapas $lookup, $group y $project.
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
  const CERRADOS = ["RESUELTO", "CERRADO"];

  // Une arreglos de arreglos sin repetir: [[a, b], [b, c]] -> [a, b, c]
  const unir = campo => ({ $reduce: { input: campo, initialValue: [], in: { $setUnion: ["$$value", "$$this"] } } });

  const pipeline = [
    // 1. Estado de cada ticket vinculado. localField es un arreglo
    //    (ticketIds): MongoDB busca cada id en el índice _id_ de tickets.
    {
      $lookup: {
        from: "tickets",
        localField: "ticketIds",
        foreignField: "_id",
        pipeline: [{ $project: { estado: 1 } }],
        as: "vinculados"
      }
    },
    // 2. Un grupo por módulo.
    {
      $group: {
        _id: "$modulo",
        articulos: { $sum: 1 },
        publicos: { $sum: { $cond: [{ $eq: ["$visibilidad", "PUBLICO"] }, 1, 0] } },
        conVector: { $sum: { $cond: [{ $ne: ["$embedding", null] }, 1, 0] } },
        idsVinculados: { $push: "$vinculados._id" },
        idsResueltos: {
          $push: {
            $map: {
              input: { $filter: { input: "$vinculados", cond: { $in: ["$$this.estado", CERRADOS] } } },
              in: "$$this._id"
            }
          }
        }
      }
    },
    // 3. Tickets distintos y promedio por artículo.
    {
      $project: {
        _id: 0,
        modulo: "$_id",
        articulos: 1,
        publicos: 1,
        internos: { $subtract: ["$articulos", "$publicos"] },
        conVector: 1,
        ticketsVinculados: { $size: unir("$idsVinculados") },
        ticketsResueltos: { $size: unir("$idsResueltos") },
        resueltosPorArticulo: { $round: [{ $divide: [{ $size: unir("$idsResueltos") }, "$articulos"] }, 1] }
      }
    }
  ];

  print("\n=== A7 · Artículos y tickets resueltos por módulo ===\n");
  const filas = base.articulos.aggregate(pipeline).toArray().sort((a, b) => (a.modulo < b.modulo ? -1 : 1));

  print(izq("Módulo", 18) + der("Artículos", 10) + der("Públicos", 10) + der("Internos", 10) + der("Con vector", 12) +
        der("Vinculados", 12) + der("Resueltos", 11) + der("Res./art.", 11));
  print("-".repeat(94));
  filas.forEach(f => print(izq(f.modulo, 18) + der(f.articulos, 10) + der(f.publicos, 10) + der(f.internos, 10) +
    der(f.conVector, 12) + der(f.ticketsVinculados, 12) + der(f.ticketsResueltos, 11) + der(f.resueltosPorArticulo, 11)));

  print("\nComprobaciones:");
  const art = filas.reduce((s, f) => s + f.articulos, 0);
  check("Suma de artículos = colección", art === base.articulos.countDocuments(), art + " / " + base.articulos.countDocuments());
  const pub = filas.reduce((s, f) => s + f.publicos, 0);
  check("Suma de públicos = artículos PUBLICO", pub === base.articulos.countDocuments({ visibilidad: "PUBLICO" }), pub);
  check("Resueltos <= vinculados en cada módulo", filas.every(f => f.ticketsResueltos <= f.ticketsVinculados));
  // Contado de otra forma: ids distintos de ticketIds por módulo. Si da
  // distinto, hay ids que apuntan a tickets borrados (HU24 debió quitarlos).
  const porModulo = {};
  base.articulos.find({}, { modulo: 1, ticketIds: 1 }).forEach(a => {
    const s = porModulo[a.modulo] = porModulo[a.modulo] || new Set();
    a.ticketIds.forEach(t => s.add(t.toString()));
  });
  check("Vinculados = ids distintos de ticketIds por módulo (sin referencias rotas)",
        filas.every(f => (porModulo[f.modulo] ? porModulo[f.modulo].size : 0) === f.ticketsVinculados));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.articulos.explain("executionStats").aggregate(pipeline));
  mostrarExplain("Pipeline", con);
  check("El $lookup a tickets usa _id_", lookupUsa(con, "tickets", "_id_"));
  print("  Nota: el reporte agrupa todos los artículos; leer los " + base.articulos.countDocuments() + " es lo esperado.");

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
