// =====================================================================
// Validador $jsonSchema de la colección empresas (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 1
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/01_empresas.js
// Crea la colección o reemplaza su validador con collMod.
//
// El backend revisa: que el CLIENTE_ADMIN solo cambie contacto (HU03).
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "empresas";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion", "nit", "razonSocial", "contacto"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        // NIT colombiano con dígito de verificación: 900123456-1
        nit: { bsonType: "string", pattern: "^[0-9]{6,10}-[0-9]$" },
        razonSocial: texto(200),
        contacto: {
          bsonType: "object",
          required: ["nombre", "email", "telefono"],
          additionalProperties: false,
          properties: {
            nombre: texto(150),
            email: { bsonType: "string", maxLength: 254, pattern: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$" },
            telefono: { bsonType: "string", pattern: "^[0-9+() -]{7,20}$" },
            direccion: texto(200),
            ciudad: texto(100)
          }
        }
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
