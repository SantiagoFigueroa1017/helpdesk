// =====================================================================
// Validador $jsonSchema de la colección capacitaciones (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 10
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/10_capacitaciones.js
// Crea la colección o reemplaza su validador con collMod.
//
// oneOf por tipo:
//   - MASIVA: empresaId y solicitudId en null
//   - PERSONALIZADA: empresaId obligatorio; solicitudId puede ser null
//     si el agente la crea sin solicitud previa
//
// El backend revisa: fechaInicio no anterior a hoy, fechaFin = inicio +
// duración, cupo no menor que cuposOcupados (HU35), cuposOcupados no mayor
// que cupo al inscribir (transacción de HU37) e instructor de alcance GLOBAL.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "capacitaciones";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });
  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "titulo", "tipo", "modulo", "empresaId", "fechaInicio", "duracionMinutos", "fechaFin",
        "cupo", "cuposOcupados", "instructorId", "estado", "solicitudId"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        titulo: texto(200),
        tipo: { enum: ["MASIVA", "PERSONALIZADA"] },
        modulo: { enum: MODULOS },
        empresaId: { bsonType: ["objectId", "null"] },
        fechaInicio: { bsonType: "date" },
        duracionMinutos: { bsonType: "int", minimum: 1, maximum: 1440 },
        fechaFin: { bsonType: "date" },
        cupo: { bsonType: "int", minimum: 1 },
        cuposOcupados: { bsonType: "int", minimum: 0 },
        instructorId: { bsonType: "objectId" },
        estado: { enum: ["PROGRAMADA", "CANCELADA"] },
        solicitudId: { bsonType: ["objectId", "null"] }
      },
      oneOf: [
        {
          title: "MASIVA",
          properties: {
            tipo: { enum: ["MASIVA"] },
            empresaId: { bsonType: "null" },
            solicitudId: { bsonType: "null" }
          }
        },
        {
          title: "PERSONALIZADA",
          properties: {
            tipo: { enum: ["PERSONALIZADA"] },
            empresaId: { bsonType: "objectId" }
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
