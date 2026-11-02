// =====================================================================
// Validador $jsonSchema de la colección solicitudesCapacitacion (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 11
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/11_solicitudesCapacitacion.js
// Crea la colección o reemplaza su validador con collMod.
//
// oneOf por estado:
//   - PENDIENTE: sin capacitación y sin motivo. atendidaPorId puede tener
//     valor porque una solicitud vuelve a PENDIENTE si se borra su
//     capacitación (HU36 v.4) y conserva quién la había programado.
//   - PROGRAMADA: capacitacionId y atendidaPorId obligatorios, sin motivo.
//   - RECHAZADA: motivoRechazo y atendidaPorId obligatorios, sin capacitación.
//
// El backend revisa: membresía con soporte al crear y el límite
// capacitacionesIncluidas del plan.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "solicitudesCapacitacion";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });
  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "empresaId", "solicitanteId", "modulo", "descripcion", "participantesEstimados",
        "estado", "capacitacionId", "motivoRechazo", "atendidaPorId"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        empresaId: { bsonType: "objectId" },
        solicitanteId: { bsonType: "objectId" },
        modulo: { enum: MODULOS },
        descripcion: texto(2000),
        participantesEstimados: { bsonType: "int", minimum: 1 },
        fechaPreferida: { bsonType: "date" },
        estado: { enum: ["PENDIENTE", "PROGRAMADA", "RECHAZADA"] },
        capacitacionId: { bsonType: ["objectId", "null"] },
        motivoRechazo: { bsonType: ["string", "null"] },
        atendidaPorId: { bsonType: ["objectId", "null"] }
      },
      oneOf: [
        {
          title: "PENDIENTE",
          properties: {
            estado: { enum: ["PENDIENTE"] },
            capacitacionId: { bsonType: "null" },
            motivoRechazo: { bsonType: "null" }
          }
        },
        {
          title: "PROGRAMADA",
          properties: {
            estado: { enum: ["PROGRAMADA"] },
            capacitacionId: { bsonType: "objectId" },
            motivoRechazo: { bsonType: "null" },
            atendidaPorId: { bsonType: "objectId" }
          }
        },
        {
          title: "RECHAZADA",
          properties: {
            estado: { enum: ["RECHAZADA"] },
            capacitacionId: { bsonType: "null" },
            motivoRechazo: texto(1000),
            atendidaPorId: { bsonType: "objectId" }
          }
        }
      ]
    }
  };

  const opciones = { validationLevel: "strict", validationAction: "error" };
  try {
    if (helpdesk.getCollectionNames().includes(NOMBRE)) {
      helpdesk.runCommand(Object.assign({ collMod: NOMBRE, validator: validador }, opciones));
      print(NOMBRE + ": validador actualizado con collMod.");
    } else {
      helpdesk.createCollection(NOMBRE, Object.assign({ validator: validador }, opciones));
      print(NOMBRE + ": colección creada con validador.");
    }
  } catch (e) {
    if (e.code === 13) print("Sin permiso para collMod. El usuario necesita el rol dbAdmin sobre helpdesk.");
    throw e;
  }
})();
