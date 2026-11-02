// =====================================================================
// 01_verificar_semilla.js · Comprobación de los datos semilla (tarea 1.5)
//
// Cuenta los documentos de cada colección contra lo que deja
// 00_datos_semilla.js y revisa en la base las reglas que no cubre el
// validador (las aplica el backend). En Atlas, además, prueba el índice
// vectorial con una consulta $vectorSearch. No modifica datos.
//
// Cómo se usa: pegar en la consola mongosh de Compass después de cargar
// la semilla. Debe terminar en RESULTADO: OK.
// =====================================================================

(() => {

const base = db.getSiblingDB("helpdesk");
let ok = 0, falla = 0;
const revisar = (nombre, cumple, detalle) => {
  if (cumple) ok++; else falla++;
  print(`${cumple ? "OK   " : "FALLA"} ${nombre}${detalle !== undefined ? "  (" + detalle + ")" : ""}`);
};

// 1. Conteos esperados. refreshTokens queda entre 20 y 25: el índice TTL
//    borra las 5 sesiones vencidas en su siguiente ciclo (cada 60 s).
const ESPERADOS = {
  roles: 5, planes: 4, empresas: 40, usuarios: 414, membresias: 38, pagos: 444,
  solicitudesCapacitacion: 14, capacitaciones: 36, inscripciones: 876, tickets: 8000,
  comentariosTicket: 5097, articulos: 40
};
print("\n-- Conteos");
for (const [c, n] of Object.entries(ESPERADOS)) {
  const real = base.getCollection(c).countDocuments();
  revisar(c, real === n, `${real} de ${n}`);
}
const tokens = base.refreshTokens.countDocuments();
revisar("refreshTokens", tokens >= 20 && tokens <= 25, `${tokens}, entre 20 y 25`);

// 2. Reglas del backend que el validador no puede revisar
print("\n-- Reglas entre colecciones");

const cuposMal = base.capacitaciones.aggregate([
  { $lookup: { from: "inscripciones", localField: "_id", foreignField: "capacitacionId", as: "ins" } },
  { $project: { cupo: 1, cuposOcupados: 1, inscritos: { $size: "$ins" } } },
  { $match: { $expr: { $or: [{ $ne: ["$cuposOcupados", "$inscritos"] }, { $gt: ["$inscritos", "$cupo"] }] } } }
]).toArray().length;
revisar("cuposOcupados = inscripciones y no supera el cupo", cuposMal === 0, `${cuposMal} capacitaciones con diferencia`);

const fueraDeModulo = base.tickets.aggregate([
  { $lookup: { from: "membresias", localField: "empresaId", foreignField: "empresaId", as: "m" } },
  { $unwind: "$m" },
  { $match: { $expr: { $not: { $in: ["$modulo", "$m.modulosContratados"] } } } },
  { $count: "n" }
]).toArray();
revisar("tickets solo de módulos contratados", fueraDeModulo.length === 0, fueraDeModulo.length ? fueraDeModulo[0].n : 0);

const solicitanteAjeno = base.tickets.aggregate([
  { $lookup: { from: "usuarios", localField: "solicitanteId", foreignField: "_id", as: "s" } },
  { $unwind: "$s" },
  { $match: { $expr: { $ne: ["$s.empresaId", "$empresaId"] } } },
  { $count: "n" }
]).toArray();
revisar("solicitante pertenece a la empresa del ticket", solicitanteAjeno.length === 0);

const pendiente = base.membresias.findOne({ estado: "PENDIENTE" });
revisar("empresa con membresía PENDIENTE sin tickets", !pendiente || base.tickets.countDocuments({ empresaId: pendiente.empresaId }) === 0);

const abiertosConCierre = base.tickets.countDocuments({ estado: { $in: ["ABIERTO", "EN_PROCESO"] }, fechaResolucion: { $ne: null } });
revisar("abiertos sin fechaResolucion", abiertosConCierre === 0, abiertosConCierre);

const corte = new Date("2026-11-01T12:00:00Z");
const futuros = base.tickets.countDocuments({ fechaActualizacion: { $gt: corte } }) + base.pagos.countDocuments({ fechaPago: { $gt: corte } });
revisar("ningún ticket ni pago después del corte (2026-11-01)", futuros === 0, futuros);

const mezclaAlcance = base.usuarios.aggregate([
  { $lookup: { from: "roles", localField: "rolIds", foreignField: "_id", as: "r" } },
  { $project: { alcances: { $setUnion: ["$r.alcance", []] } } },
  { $match: { $expr: { $ne: [{ $size: "$alcances" }, 1] } } }
]).toArray().length;
revisar("cada usuario con roles de un solo alcance", mezclaAlcance === 0, mezclaAlcance);

// 3. Datos que necesitan los reportes de la 1.6
print("\n-- Datos para los reportes");
const susp = base.membresias.find({ estado: "SUSPENDIDA" }, { empresaId: 1 }).toArray();
const suspConAbiertos = susp.filter(m => base.tickets.countDocuments({ empresaId: m.empresaId, estado: { $in: ["ABIERTO", "EN_PROCESO"] } }) > 0).length;
revisar("A8: suspendidas con tickets abiertos", suspConAbiertos > 0, `${suspConAbiertos} de ${susp.length}`);
revisar("A5: pagos fallidos", base.pagos.countDocuments({ resultado: "FALLIDO" }) > 0, base.pagos.countDocuments({ resultado: "FALLIDO" }));
revisar("A6: asistencias marcadas", base.inscripciones.countDocuments({ asistio: { $ne: null } }) > 0, base.inscripciones.countDocuments({ asistio: { $ne: null } }));
revisar("A7: artículos con tickets vinculados", base.articulos.countDocuments({ "ticketIds.0": { $exists: true } }) > 0, base.articulos.countDocuments({ "ticketIds.0": { $exists: true } }));
const finVentana = new Date(corte.getTime() - 90 * 24 * 3600 * 1000);
const capsA9 = base.capacitaciones.countDocuments({ estado: "PROGRAMADA", fechaFin: { $lte: finVentana, $gte: new Date("2026-01-30T00:00:00Z") } });
revisar("A9: capacitaciones terminadas con ventana completa de 90 días", capsA9 > 0, capsA9);

// 4. Índice vectorial (solo Atlas)
print("\n-- Índice vectorial");
const conVector = base.articulos.countDocuments({ embedding: { $ne: null } });
const sinVector = base.articulos.countDocuments({ embedding: null });
print(`Artículos con vector: ${conVector} · con embedding null: ${sinVector}`);
const muestra = base.articulos.findOne({ embedding: { $ne: null } }, { embedding: 1, modulo: 1 });
try {
  const res = base.articulos.aggregate([
    { $vectorSearch: { index: "vec_articulos_embedding", path: "embedding", queryVector: muestra.embedding, numCandidates: 100, limit: 40 } },
    { $project: { modulo: 1, puntaje: { $meta: "vectorSearchScore" } } }
  ]).toArray();
  revisar("$vectorSearch devuelve solo artículos con vector", res.length === conVector, `${res.length} resultados; ${conVector} esperados`);
  const filtrado = base.articulos.aggregate([
    { $vectorSearch: { index: "vec_articulos_embedding", path: "embedding", queryVector: muestra.embedding, numCandidates: 100, limit: 40, filter: { modulo: muestra.modulo, visibilidad: "PUBLICO" } } },
    { $project: { modulo: 1, visibilidad: 1 } }
  ]).toArray();
  revisar("filtro por modulo y visibilidad", filtrado.every(a => a.modulo === muestra.modulo && a.visibilidad === "PUBLICO"), `${filtrado.length} resultados de ${muestra.modulo}`);
} catch (e) {
  print(`$vectorSearch no disponible en este servidor (${e.codeName || e.code || e.message}). En local es lo esperado.`);
}

print(`\nOK: ${ok} · FALLA: ${falla}`);
print(falla === 0 ? "RESULTADO: OK" : "RESULTADO: REVISAR las filas en FALLA");

})();
