// =====================================================================
// Validador $jsonSchema de la colección refreshTokens (tarea 1.3, tokenHash en 2.4)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 13
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/13_refreshTokens.js
// Crea la colección o reemplaza su validador con collMod.
//
// tokenHash es el SHA-256 del refresh token en hexadecimal minúscula:
// exactamente 64 caracteres de 0-9 y a-f (DECIDIDO en 2.4). El token que
// recibe el cliente (32 bytes aleatorios en Base64URL) nunca se guarda.
// expiraEn debe ser date para que funcione el índice TTL de la tarea 1.4.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "refreshTokens";

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "usuarioId", "tokenHash", "expiraEn"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        usuarioId: { bsonType: "objectId" },
        tokenHash: { bsonType: "string", pattern: "^[0-9a-f]{64}$" },
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
