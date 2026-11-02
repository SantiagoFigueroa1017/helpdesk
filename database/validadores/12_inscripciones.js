// =====================================================================
// Validador $jsonSchema de la colección inscripciones (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 12
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/12_inscripciones.js
// Crea la colección o reemplaza su validador con collMod.
//
// oneOf de asistencia (HU39):
//   - sin marcar: asistio, marcadoPorId y fechaMarcado en null
//   - marcada: los tres con valor
//
// El backend revisa: cupo disponible (transacción de HU37), que empresaId
// sea la del usuario y que solo el instructor o el ADMIN marquen.
// La unicidad capacitación-usuario la da el índice único de la tarea 1.4.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "inscripciones";

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "capacitacionId", "usuarioId", "empresaId", "asistio", "marcadoPorId", "fechaMarcado"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        capacitacionId: { bsonType: "objectId" },
        usuarioId: { bsonType: "objectId" },
        empresaId: { bsonType: "objectId" },
        asistio: { bsonType: ["bool", "null"] },
        marcadoPorId: { bsonType: ["objectId", "null"] },
        fechaMarcado: { bsonType: ["date", "null"] }
      },
      oneOf: [
        {
          title: "sin marcar",
          properties: {
            asistio: { bsonType: "null" },
            marcadoPorId: { bsonType: "null" },
            fechaMarcado: { bsonType: "null" }
          }
        },
        {
          title: "marcada",
          properties: {
            asistio: { bsonType: "bool" },
            marcadoPorId: { bsonType: "objectId" },
            fechaMarcado: { bsonType: "date" }
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
