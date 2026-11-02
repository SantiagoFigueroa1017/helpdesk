// =====================================================================
// Validador $jsonSchema de la colección comentariosTicket (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 8
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/08_comentariosTicket.js
// Crea la colección o reemplaza su validador con collMod.
//
// El backend revisa: que solo el autor edite y que el ticket sea visible
// para quien comenta.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "comentariosTicket";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "ticketId", "autorId", "texto", "editado"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        ticketId: { bsonType: "objectId" },
        autorId: { bsonType: "objectId" },
        texto: texto(5000),
        editado: { bsonType: "bool" }
      }
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
