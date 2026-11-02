// =====================================================================
// Validador $jsonSchema de la colección planes (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 4
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/04_planes.js
// Crea la colección o reemplaza su validador con collMod.
//
// precio exige Decimal128. Desde mongosh se escribe NumberDecimal("150000").
// Un número normal (150000) se guarda como int o double y se rechaza.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "planes";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });
  const entero = (min, max) => {
    const s = { bsonType: "int", minimum: min };
    if (max !== undefined) s.maximum = max;
    return s;
  };

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "nombre", "descripcion", "precio", "nivelAcceso", "limites", "activo"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        nombre: texto(100),
        descripcion: texto(1000),
        precio: { bsonType: "decimal", minimum: 0, exclusiveMinimum: true },
        nivelAcceso: { enum: ["BASICO", "ESTANDAR", "PREMIUM"] },
        limites: {
          bsonType: "object",
          required: ["horasRespuesta", "capacitacionesIncluidas", "maxUsuarios", "maxModulos"],
          additionalProperties: false,
          properties: {
            horasRespuesta: entero(1),
            capacitacionesIncluidas: entero(0),
            maxUsuarios: entero(1),
            maxModulos: entero(1, 5)
          }
        },
        activo: { bsonType: "bool" }
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
