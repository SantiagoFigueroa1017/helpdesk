// =====================================================================
// A3 · Carga de tickets abiertos por agente
// Historia: HU06 (consulta de usuarios) · Etapas: $match, $lookup, $project, $sort
// Colección: usuarios · Índices: ix_usuarios_rolIds y ix_tickets_agente_estado_fecha
//
// Sirve al ADMIN para repartir la carga (TICKETS_ASIGNAR_OTROS).
// "Agente" = usuario con un rol activo de alcance GLOBAL que tenga el
// permiso TICKETS_ASIGNAR. Se decide por permiso y no por el nombre del
// rol, igual que la autorización del backend (contexto 8.4).
// Abierto = ABIERTO o EN_PROCESO (decisión de 1.2).
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

  // Roles que permiten atender tickets. En el backend sale del catálogo.
  const rolesAgente = base.roles
    .find({ permisos: "TICKETS_ASIGNAR", alcance: "GLOBAL", activo: true }, { _id: 1, nombre: 1 })
    .toArray();

  const pipeline = [
    // 1. Usuarios con alguno de esos roles. rolIds es multikey.
    { $match: { rolIds: { $in: rolesAgente.map(r => r._id) } } },
    // 2. Sus tickets abiertos. La igualdad agenteId + estado usa
    //    ix_tickets_agente_estado_fecha.
    {
      $lookup: {
        from: "tickets",
        localField: "_id",
        foreignField: "agenteId",
        pipeline: [
          { $match: { estado: { $in: ABIERTOS } } },
          { $project: { _id: 0, estado: 1, fechaCreacion: 1 } }
        ],
        as: "abiertos"
      }
    },
    // 3. Conteos y antigüedad del ticket más viejo.
    {
      $project: {
        _id: 0,
        nombre: 1,
        email: 1,
        activo: 1,
        abiertos: { $size: "$abiertos" },
        enProceso: { $size: { $filter: { input: "$abiertos", cond: { $eq: ["$$this.estado", "EN_PROCESO"] } } } },
        masAntiguo: { $min: "$abiertos.fechaCreacion" }
      }
    },
    // 4. El más cargado primero; a igual carga, el de ticket más viejo.
    { $sort: { abiertos: -1, masAntiguo: 1, nombre: 1 } }
  ];

  print("\n=== A3 · Carga de tickets abiertos por agente ===");
  print("Roles que cuentan como agente: " + rolesAgente.map(r => r.nombre).join(", ") + "\n");
  const filas = base.usuarios.aggregate(pipeline).toArray();

  print(izq("Agente", 28) + izq("Estado", 10) + der("Abiertos", 9) + der("En proceso", 12) + der("Más antiguo", 13) + der("Días", 6));
  print("-".repeat(78));
  filas.forEach(f => print(izq(f.nombre, 28) + izq(f.activo ? "activo" : "inactivo", 10) + der(f.abiertos, 9) +
    der(f.enProceso, 12) + der(dia(f.masAntiguo), 13) +
    der(f.masAntiguo ? Math.floor((ahora - f.masAntiguo) / 86400000) : "-", 6)));
  const sinAsignar = base.tickets.countDocuments({ agenteId: null, estado: { $in: ABIERTOS } });
  print("\nTickets abiertos sin asignar: " + sinAsignar);

  print("\nComprobaciones:");
  const asignados = filas.reduce((s, f) => s + f.abiertos, 0);
  const totalAbiertos = base.tickets.countDocuments({ estado: { $in: ABIERTOS } });
  check("Abiertos por agente + sin asignar = total abiertos", asignados + sinAsignar === totalAbiertos,
        asignados + " + " + sinAsignar + " / " + totalAbiertos);
  check("Orden descendente por carga", filas.every((f, i) => i === 0 || filas[i - 1].abiertos >= f.abiertos));
  check("Ningún agente inactivo con tickets abiertos", filas.every(f => f.activo || f.abiertos === 0));

  print("\nexplain(\"executionStats\"):");
  const con = resumirExplain(base.usuarios.explain("executionStats").aggregate(pipeline));
  const sin = resumirExplain(base.usuarios.explain("executionStats").aggregate(pipeline, { hint: { $natural: 1 } }));
  mostrarExplain("Con índice", con);
  mostrarExplain("Sin índice", sin);
  check("Usa ix_usuarios_rolIds", usaIndice(con, "ix_usuarios_rolIds"));
  check("El $lookup a tickets usa ix_tickets_agente_estado_fecha", lookupUsa(con, "tickets", "ix_tickets_agente_estado_fecha"));
  check("Sin índice recorre la colección", sin.collscan);

  print("\nRESULTADO: " + (fallas === 0 ? "OK" : "FALLA (" + fallas + ")"));
})();
