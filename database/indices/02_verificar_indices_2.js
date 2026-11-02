// =====================================================================
// 02_verificar_indices.js · Qué consulta atiende cada índice (tarea 1.4)
//
// Corre la consulta representativa de cada índice con
// explain("executionStats") y compara el índice que eligió el planificador
// con el esperado. No modifica datos.
//
// Cómo se usa: pegar en la consola mongosh de Compass (Atlas o local)
// después de 00_indices.js.
//
// Resultados posibles por fila:
//   OK         el planificador usó el índice esperado.
//   SIN DATOS  usó otro índice, pero la colección está vacía y había varios
//              candidatos empatados. Con colecciones vacías el planificador
//              no tiene cómo desempatar. Se repite en 1.5 con datos semilla.
//   FALLA      usó otro índice o COLLSCAN y la colección tiene documentos,
//              o el índice esperado no existe.
//
// Valores de las consultas (cambio de 1.5): si la colección tiene datos,
// se toman de un documento real (una empresa, un agente con tickets, un
// solicitante con incidentes del módulo). Si está vacía, se usan ObjectId
// de relleno. Con valores inventados dos índices pueden empatar (los dos
// terminan de inmediato sin resultados) y el planificador elige cualquiera.
//
// Columna "sin índice": la misma consulta forzada a recorrer la colección
// con hint({ $natural: 1 }). Compara los documentos examinados con índice
// y sin él. No aplica a $text, que exige su índice.
// =====================================================================

(() => {

const base = db.getSiblingDB("helpdesk");
const id = () => new ObjectId();
const ahora = new Date();
const hace90 = new Date(ahora.getTime() - 90 * 24 * 3600 * 1000);
const en90 = new Date(ahora.getTime() + 90 * 24 * 3600 * 1000);

// Valor de un documento real, o el de relleno si no hay datos.
const de = (coleccion, filtro, campo, relleno) => {
  const d = base.getCollection(coleccion).findOne(filtro, { [campo]: 1 });
  return d && d[campo] !== undefined && d[campo] !== null ? d[campo] : relleno;
};
const M = {
  nit: de("empresas", {}, "nit", "900123456-1"),
  email: de("usuarios", { empresaId: { $ne: null } }, "email", "camilo.quintero@ufpso.edu.co"),
  rolCliente: de("roles", { nombre: "CLIENTE" }, "_id", id()),
  empresaUsuarios: de("usuarios", { empresaId: { $ne: null } }, "empresaId", id()),
  empresaMembresia: de("membresias", {}, "empresaId", id()),
  plan: de("membresias", {}, "planId", id()),
  empresaPagos: de("pagos", {}, "empresaId", id()),
  membresiaPagos: de("pagos", {}, "membresiaId", id()),
  empresaTickets: de("tickets", { estado: "ABIERTO" }, "empresaId", id()),
  agente: de("tickets", { estado: "EN_PROCESO" }, "agenteId", id()),
  solicitante: de("tickets", { estado: "ABIERTO" }, "solicitanteId", id()),
  creador: de("tickets", {}, "creadorId", id()),
  ticketComentado: de("comentariosTicket", {}, "ticketId", id()),
  ticketEnArticulo: (de("articulos", { "ticketIds.0": { $exists: true } }, "ticketIds", [id()]))[0],
  empresaPersonalizada: de("capacitaciones", { tipo: "PERSONALIZADA" }, "empresaId", id()),
  instructor: de("capacitaciones", { estado: "PROGRAMADA", fechaFin: { $gt: ahora } }, "instructorId", id()),
  empresaSolicitud: de("solicitudesCapacitacion", {}, "empresaId", id()),
  capacitacionInscritos: de("inscripciones", {}, "capacitacionId", id()),
  usuarioInscrito: de("inscripciones", {}, "usuarioId", id()),
  tokenHash: de("refreshTokens", {}, "tokenHash", "a".repeat(64)),
  usuarioToken: de("refreshTokens", {}, "usuarioId", id())
};
// A9: un incidente real; la ventana va 90 días antes y después de su fecha.
const inc = base.tickets.findOne({ tipo: "INCIDENTE" }, { solicitanteId: 1, modulo: 1, fechaCreacion: 1 });
const A9 = inc
  ? { solicitanteId: inc.solicitanteId, modulo: inc.modulo, desde: new Date(inc.fechaCreacion.getTime() - 90 * 24 * 3600 * 1000), hasta: new Date(inc.fechaCreacion.getTime() + 90 * 24 * 3600 * 1000) }
  : { solicitanteId: id(), modulo: "INVENTARIOS", desde: hace90, hasta: en90 };

// [colección, índice esperado, qué atiende, función que devuelve el cursor]
const CASOS = [
  ["empresas", "ux_empresas_nit", "HU01 NIT repetido", c => c.find({ nit: M.nit })],
  ["empresas", "ix_empresas_razonSocial", "HU02 buscar por razón social", c => c.find({ razonSocial: /^Distri/ }).sort({ razonSocial: 1 })],
  ["roles", "ux_roles_nombre", "HU25 nombre repetido", c => c.find({ nombre: "CLIENTE" })],
  ["usuarios", "ux_usuarios_email", "Login", c => c.find({ email: M.email })],
  ["usuarios", "ix_usuarios_rolIds", "HU28 usuarios con el rol", c => c.find({ rolIds: M.rolCliente })],
  ["usuarios", "ix_usuarios_empresa_nombre", "HU06 usuarios de una empresa", c => c.find({ empresaId: M.empresaUsuarios }).sort({ nombre: 1 })],
  ["planes", "ux_planes_nombre", "HU09 nombre repetido", c => c.find({ nombre: "Premium" })],
  ["membresias", "ux_membresias_empresa", "Verificación de soporte", c => c.find({ empresaId: M.empresaMembresia })],
  ["membresias", "ix_membresias_plan_estado", "HU12 membresías del plan", c => c.find({ planId: M.plan })],
  ["pagos", "ix_pagos_empresa_fecha", "HU18 pagos de una empresa", c => c.find({ empresaId: M.empresaPagos }).sort({ fechaPago: -1 })],
  ["pagos", "ix_pagos_resultado_fecha", "A5 fallidos en un rango", c => c.find({ resultado: "FALLIDO", fechaPago: { $gte: hace90, $lt: ahora } })],
  ["pagos", "ix_pagos_membresia", "HU16 pagos de la membresía", c => c.find({ membresiaId: M.membresiaPagos })],
  ["tickets", "ix_tickets_empresa_estado_fecha", "HU22 tickets abiertos de una empresa", c => c.find({ empresaId: M.empresaTickets, estado: "ABIERTO" }).sort({ fechaCreacion: -1 })],
  ["tickets", "ix_tickets_modulo_tipo_fecha", "A1 incidentes por módulo", c => c.find({ modulo: "PRODUCCION", tipo: "INCIDENTE" }).sort({ fechaCreacion: -1 })],
  ["tickets", "ix_tickets_agente_estado_fecha", "Bandeja del agente", c => c.find({ agenteId: M.agente, estado: "EN_PROCESO" }).sort({ fechaCreacion: -1 })],
  ["tickets", "ix_tickets_solicitante_estado_fecha", "HU22 mis tickets", c => c.find({ solicitanteId: M.solicitante, estado: "ABIERTO" }).sort({ fechaCreacion: -1 })],
  ["tickets", "ix_tickets_solicitante_modulo_fecha", "A9 incidentes en la ventana", c => c.find({ solicitanteId: A9.solicitanteId, modulo: A9.modulo, tipo: "INCIDENTE", fechaCreacion: { $gte: A9.desde, $lt: A9.hasta } })],
  ["tickets", "ix_tickets_estado_fechaLimite", "HU22 v.4 por vencimiento", c => c.find({ estado: "ABIERTO" }).sort({ fechaLimiteRespuesta: 1 })],
  ["tickets", "ix_tickets_creador", "HU08 tickets creados por el usuario", c => c.find({ creadorId: M.creador })],
  ["comentariosTicket", "ix_comentarios_ticket_fecha", "Conversación del ticket", c => c.find({ ticketId: M.ticketComentado }).sort({ fechaCreacion: 1 })],
  ["articulos", "txt_articulos", "HU30 búsqueda por palabras (cliente)", c => c.find({ $text: { $search: "error sincronización pasarela" }, visibilidad: "PUBLICO" })],
  ["articulos", "ix_articulos_ticketIds", "HU24 artículos que citan el ticket", c => c.find({ ticketIds: M.ticketEnArticulo })],
  ["capacitaciones", "ix_capacitaciones_fecha_modulo", "HU34 cronograma", c => c.find({ fechaInicio: { $gte: ahora } }).sort({ fechaInicio: 1 })],
  ["capacitaciones", "ix_capacitaciones_empresa_fecha", "HU34 personalizadas de la empresa", c => c.find({ empresaId: M.empresaPersonalizada }).sort({ fechaInicio: 1 })],
  ["capacitaciones", "ix_capacitaciones_instructor_fecha", "HU08 instructor con sesiones futuras", c => c.find({ instructorId: M.instructor, estado: "PROGRAMADA", fechaFin: { $gt: ahora } })],
  ["solicitudesCapacitacion", "ix_solicitudes_estado_fecha", "Bandeja de solicitudes", c => c.find({ estado: "PENDIENTE" }).sort({ fechaCreacion: 1 })],
  ["solicitudesCapacitacion", "ix_solicitudes_empresa_fecha", "Solicitudes de la empresa", c => c.find({ empresaId: M.empresaSolicitud }).sort({ fechaCreacion: -1 })],
  ["inscripciones", "ux_inscripciones_capacitacion_usuario", "A6, HU36 inscritos de la capacitación", c => c.find({ capacitacionId: M.capacitacionInscritos })],
  ["inscripciones", "ix_inscripciones_usuario_fecha", "HU38 mis inscripciones", c => c.find({ usuarioId: M.usuarioInscrito }).sort({ fechaCreacion: -1 })],
  ["refreshTokens", "ux_refresh_tokenHash", "Rotación", c => c.find({ tokenHash: M.tokenHash })],
  ["refreshTokens", "ix_refresh_usuario", "Logout de todas las sesiones", c => c.find({ usuarioId: M.usuarioToken })]
];

// Recorre el árbol del plan y junta los nombres de índice y las etapas.
function recorrer(nodo, indices, etapas) {
  if (!nodo || typeof nodo !== "object") return;
  if (nodo.stage) etapas.push(nodo.stage);
  if (nodo.indexName) indices.push(nodo.indexName);
  if (nodo.inputStage) recorrer(nodo.inputStage, indices, etapas);
  if (Array.isArray(nodo.inputStages)) nodo.inputStages.forEach(n => recorrer(n, indices, etapas));
  if (nodo.queryPlan) recorrer(nodo.queryPlan, indices, etapas); // formato del motor SBE
}

const filas = [];
let ok = 0, sinDatos = 0, falla = 0;

for (const [coleccion, esperado, atiende, consulta] of CASOS) {
  const col = base.getCollection(coleccion);
  const existe = col.getIndexes().some(i => i.name === esperado);
  if (!existe) {
    falla++;
    filas.push({ coleccion, esperado, usado: "(no existe)", docs: "-", claves: "-", sinIndice: "-", resultado: "FALLA", atiende });
    continue;
  }

  const exp = consulta(col).explain("executionStats");
  const qp = exp.queryPlanner;
  const indices = [], etapas = [];
  recorrer(qp.winningPlan, indices, etapas);
  const usado = indices.length ? [...new Set(indices)].join("+") : (etapas.includes("COLLSCAN") ? "COLLSCAN" : etapas.join(">"));
  const habiaCandidatos = (qp.rejectedPlans || []).length > 0;
  const total = col.estimatedDocumentCount();

  // La misma consulta recorriendo toda la colección.
  let sinIndice = "-";
  try { sinIndice = consulta(col).hint({ $natural: 1 }).explain("executionStats").executionStats.totalDocsExamined; }
  catch (e) { /* $text no admite hint $natural */ }

  let resultado;
  if (indices.includes(esperado)) { resultado = "OK"; ok++; }
  else if (total === 0 && habiaCandidatos) { resultado = "SIN DATOS"; sinDatos++; }
  else { resultado = "FALLA"; falla++; }

  filas.push({
    coleccion,
    esperado,
    usado,
    docs: exp.executionStats.totalDocsExamined,
    claves: exp.executionStats.totalKeysExamined,
    sinIndice,
    resultado,
    atiende
  });
}

// El TTL no atiende una consulta: lo usa el proceso de MongoDB que borra
// documentos vencidos cada 60 segundos. Se revisa la opción.
const ttl = base.refreshTokens.getIndexes().find(i => i.name === "ttl_refresh_expiraEn");
const ttlOk = !!ttl && ttl.expireAfterSeconds === 0;
if (ttlOk) ok++; else falla++;
filas.push({
  coleccion: "refreshTokens",
  esperado: "ttl_refresh_expiraEn",
  usado: ttl ? `expireAfterSeconds=${ttl.expireAfterSeconds}` : "(no existe)",
  docs: "-", claves: "-", sinIndice: "-",
  resultado: ttlOk ? "OK" : "FALLA",
  atiende: "Limpieza automática de sesiones vencidas"
});

// Una línea por consulta. No se usa console.table porque la consola
// mongosh de Compass no la muestra.
for (const f of filas) {
  print(`${f.resultado.padEnd(9)} ${f.coleccion}.${f.esperado}  usado: ${f.usado}  docs: ${f.docs}  claves: ${f.claves}  sin índice: ${f.sinIndice}  (${f.atiende})`);
}

print(`\nOK: ${ok} · SIN DATOS: ${sinDatos} · FALLA: ${falla} · total: ${filas.length}`);
for (const f of filas.filter(x => x.resultado !== "OK")) {
  print(`  ${f.resultado}: ${f.coleccion}.${f.esperado} eligió ${f.usado}`);
}
if (falla > 0) print("Hay filas en FALLA: revisa que 00_indices.js haya terminado con RESULTADO: OK.");
if (sinDatos > 0) print("Las filas SIN DATOS se confirman en la tarea 1.5, con los datos semilla cargados.");

})();
