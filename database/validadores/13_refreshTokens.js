// =====================================================================
// Validador $jsonSchema de la colección refreshTokens (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 13
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/13_refreshTokens.js
// Crea la colección o reemplaza su validador con collMod.
//
// tokenHash acepta cualquier texto de hasta 128 caracteres: la función de
// hash se elige en 2.4. Si se fija SHA-256, se puede cambiar por
// pattern "^[0-9a-f]{64}$".
// expiraEn debe ser date para que funcione el índice TTL de la tarea 1.4.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "refreshTokens";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "usuarioId", "tokenHash", "expiraEn"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        usuarioId: { bsonType: "objectId" },
        tokenHash: texto(128),
        expiraEn: { bsonType: "date" }
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
