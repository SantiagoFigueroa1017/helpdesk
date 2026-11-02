// =====================================================================
// Validador $jsonSchema de la colección membresias (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 5
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/05_membresias.js
// Crea la colección o reemplaza su validador con collMod.
//
// No hay estado VENCIDA: se calcula con fechaVencimiento en cada petición.
//
// El backend revisa: fechaVencimiento posterior a fechaInicio, plan activo
// al asignar, maxModulos del plan y que empresaId no cambie (HU15).
// La unicidad de empresaId la da el índice único de la tarea 1.4.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "membresias";

  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "empresaId", "planId", "modulosContratados", "fechaInicio", "fechaVencimiento", "estado"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        empresaId: { bsonType: "objectId" },
        planId: { bsonType: "objectId" },
        modulosContratados: {
          bsonType: "array",
          minItems: 1,
          maxItems: 5,
          uniqueItems: true,
          items: { enum: MODULOS }
        },
        fechaInicio: { bsonType: "date" },
        fechaVencimiento: { bsonType: "date" },
        estado: { enum: ["PENDIENTE", "ACTIVA", "SUSPENDIDA"] }
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
