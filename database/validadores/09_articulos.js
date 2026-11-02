// =====================================================================
// Validador $jsonSchema de la colección articulos (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 9
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/09_articulos.js
// Crea la colección o reemplaza su validador con collMod.
//
// embedding y embeddingModelo van juntos (oneOf de 2 ramas):
//   - pendiente: los dos en null
//   - generado: 768 valores double y el nombre del modelo
// Así no puede quedar un vector sin modelo ni un modelo sin vector.
// Cada valor del vector debe ser double. Java (List<Double>) ya los guarda
// así; desde mongosh un 0 o un 1 exactos se guardarían como int.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "articulos";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });
  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];
  const DIMENSIONES = 768;

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "titulo", "contenido", "modulo", "etiquetas", "visibilidad", "ticketIds",
        "autorId", "embedding", "embeddingModelo"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        titulo: texto(200),
        contenido: texto(50000),
        modulo: { enum: MODULOS },
        etiquetas: {
          bsonType: "array",
          maxItems: 20,
          uniqueItems: true,
          items: texto(50)
        },
        visibilidad: { enum: ["PUBLICO", "INTERNO"] },
        ticketIds: {
          bsonType: "array",
          maxItems: 500,
          uniqueItems: true,
          items: { bsonType: "objectId" }
        },
        autorId: { bsonType: "objectId" },
        embedding: { bsonType: ["array", "null"] },
        embeddingModelo: { bsonType: ["string", "null"] }
      },
      oneOf: [
        {
          title: "embedding pendiente",
          properties: {
            embedding: { bsonType: "null" },
            embeddingModelo: { bsonType: "null" }
          }
        },
        {
          title: "embedding generado",
          properties: {
            embedding: {
              bsonType: "array",
              minItems: DIMENSIONES,
              maxItems: DIMENSIONES,
              items: { bsonType: "double" }
            },
            embeddingModelo: texto(100)
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
