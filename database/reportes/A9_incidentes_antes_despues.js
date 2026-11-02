// =====================================================================
// A9 · Incidentes antes y después de asistir a la capacitación del módulo
// Extra, fuera del documento de requisitos (decisión de 1.2)
// Etapas: $match, $lookup, $project, $group, $sort
// Colección: inscripciones · Índice: ix_tickets_solicitante_modulo_fecha
//
// Responde al enunciado: las capacitaciones existen "para reducir la tasa
// de errores de los usuarios". Por cada asistencia cuenta los incidentes
// que ese usuario reportó en el módulo de la capacitación durante los 90
// días anteriores al fin de la sesión y los 90 días posteriores.
//
// Ventana completa: solo entran las capacitaciones cuyas dos ventanas
// caben entre el primer y el último ticket registrados. Si la ventana de
// antes empezara antes del primer ticket, o la de después terminara
// después del último, se compararían periodos de distinto largo.
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
  // ---- Parámetro: ventana en días (DECIDIDO en 1.6: 90) ---------------
  const DIAS = 90;
  const VENTANA = DIAS * 24 * 3600 * 1000;

  // Primer y último ticket. El _id (ObjectId) empieza por la fecha de
  // inserción, así que ordenar por _id usa el índice _id_ y no recorre.
  const primero = base.tickets.find({}, { fechaCreacion: 1 }).sort({ _id: 1 }).limit(1).toArray()[0];
  const ultimo = base.tickets.find({}, { fechaCreacion: 1 }).sort({ _id: -1 }).limit(1).toArray()[0];
  if (!primero) { print("No hay tickets."); return; }
  const finMinimo = new Date(primero.fechaCreacion.getTime() + VENTANA);   // fechaFin desde
  const finMaximo = new Date(ultimo.fechaCreacion.getTime() - VENTANA);    // fechaFin hasta

  const pipeline = [
    // 1. Solo asistencias marcadas como "asistió".
    { $match: { asistio: true } },
    // 2. Capacitación con ventana completa (no cancelada).
    {
      $lookup: {
        from: "capacitaciones",
        localField: "capacitacionId",
        foreignField: "_id",
        pipeline: [
          { $match: { estado: "PROGRAMADA", fechaFin: { $gte: finMinimo, $lte: finMaximo } } },
          { $project: { _id: 0, modulo: 1, fechaFin: 1 } }
        ],
        as: "cap"
      }
    },
    { $match: { "cap.0": { $exists: true } } },
    // 3. Incidentes del mismo usuario y módulo en las dos ventanas.
    //    Igualdad solicitanteId + modulo y rango de fechaCreacion: el orden
    //    exacto de ix_tickets_solicitante_modulo_fecha (igualdad, igualdad, rango).
    {
      $lookup: {
        from: "tickets",
        let: { usuario: "$usuarioId", modulo: { $first: "$cap.modulo" }, fin: { $first: "$cap.fechaFin" } },
        pipeline: [
          {
            $match: {
              $expr: {
                $and: [
                  { $eq: ["$solicitanteId", "$$usuario"] },
                  { $eq: ["$modulo", "$$modulo"] },
                  { $gte: ["$fechaCreacion", { $subtract: ["$$fin", VENTANA] }] },
                  { $lt: ["$fechaCreacion", { $add: ["$$fin", VENTANA] }] }
                ]
              },
              tipo: "INCIDENTE"
            }
          },
          { $project: { _id: 0, fechaCreacion: 1 } }
        ],
        as: "incidentes"
      }
    },
    // 4. Conteo de cada lado de fechaFin.
    {
      $project: {
        _id: 0,
        modulo: { $first: "$cap.modulo" },
        antes: { $size: { $filter: { input: "$incidentes", cond: { $lt: ["$$this.fechaCreacion", { $first: "$cap.fechaFin" }] } } } },
        despues: { $size: { $filter: { input: "$incidentes", cond: { $gte: ["$$this.fechaCreacion", { $first: "$cap.fechaFin" }] } } } }
      }
    },
    // 5. Totales por módulo.
    { $group: { _id: "$modulo", asistencias: { $sum: 1 }, antes: { $sum: "$antes" }, despues: { $sum: "$despues" } } },
    // 6. Variación porcentual; null si antes = 0 (no hay base para comparar).
    {
      $project: {
        _id: 0,
        modulo: "$_id",
        asistencias: 1,
        antes: 1,
        despues: 1,
        variacionPorcentual: {
          $cond: [
            { $gt: ["$antes", 0] },
            { $round: [{ $multiply: [{ $divide: [{ $subtract: ["$despues", "$antes"] }, "$antes"] }, 100] }, 1] },
            null
          ]
        }
      }
    },
    // 7. Arriba los módulos donde más bajaron los incidentes.
    { $sort: { variacionPorcentual: 1, modulo: 1 } }
  ];

  print("\n=== A9 · Incidentes antes y después de la capacitación ===");
  print("Ventana: " + DIAS + " días a cada lado de la fecha de fin de la sesión");
  print("Tickets registrados del " + dia(primero.fechaCreacion) + " al " + dia(ultimo.fechaCreacion) +
        " -> entran las sesiones que terminaron entre " + dia(finMinimo) + " y " + dia(finMaximo) + "\n");
  const filas = base.inscripciones.aggregate(pipeline).toArray();

  print(izq("Módulo", 18) + der("Asistencias", 13) + der("Antes", 8) + der("Después", 9) + der("Variación %", 13));
  print("-".repeat(61));
  filas.forEach(f => print(izq(f.modulo, 18) + der(f.asistencias, 13) + der(f.antes, 8) + der(f.despues, 9) +
    der(f.variacionPorcentual === null ? "-" : (f.variacionPorcentual > 0 ? "+" : "") + f.variacionPorcentual, 13)));

  print("\nComprobaciones:");
  const capsValidas = base.capacitaciones
    .find({ estado: "PROGRAMADA", fechaFin: { $gte: finMinimo, $lte: finMaximo } }, { _id: 1 }).toArray().map(c => c._id);
  const asistBD = base.inscripciones.countDocuments({ asistio: true, capacitacionId: { $in: capsValidas } });
  const asist = filas.reduce((s, f) => s + f.asistencias, 0);
  check("Asistencias contadas = asistencias a sesiones con ventana completa", asist === asistBD,
        asist + " / " + asistBD + " en " + capsValidas.length + " sesiones");
  check("Hay sesiones con ventana completa", capsValidas.length > 0, capsValidas.length);
  check("Orden ascendente por variación",
        filas.every((f, i) => i === 0 || f.variacionPorcentual === null || filas[i - 1].variacionPorcentual <= f.variacionPorcentual));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.inscripciones.explain("executionStats").aggregate(pipeline));
  mostrarExplain("Pipeline", con);
  check("El $lookup a tickets usa ix_tickets_solicitante_modulo_fecha",
        lookupUsa(con, "tickets", "ix_tickets_solicitante_modulo_fecha"));
  check("El $lookup a capacitaciones usa _id_", lookupUsa(con, "capacitaciones", "_id_"));
  print("  Nota: inscripciones no tiene índice por asistio; recorrer las " + base.inscripciones.countDocuments() +
        " es lo esperado.");

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
