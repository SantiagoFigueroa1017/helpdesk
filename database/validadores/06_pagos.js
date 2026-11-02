// =====================================================================
// Validador $jsonSchema de la colección pagos (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 6
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/06_pagos.js
// Crea la colección o reemplaza su validador con collMod.
//
// metodoPago confirmado en el chat 1.3.
// monto exige Decimal128 mayor que cero: NumberDecimal("150000") en mongosh.
//
// El backend revisa: que empresaId sea la de la membresía y la transacción
// que cambia el estado de la membresía (HU17, HU19).
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "pagos";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "membresiaId", "empresaId", "monto", "fechaPago", "metodoPago", "referencia",
        "resultado", "registradoPorId"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        membresiaId: { bsonType: "objectId" },
        empresaId: { bsonType: "objectId" },
        monto: { bsonType: "decimal", minimum: 0, exclusiveMinimum: true },
        fechaPago: { bsonType: "date" },
        metodoPago: { enum: ["TRANSFERENCIA", "TARJETA", "PSE", "EFECTIVO", "CONSIGNACION"] },
        referencia: texto(100),
        resultado: { enum: ["APROBADO", "FALLIDO"] },
        registradoPorId: { bsonType: "objectId" }
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
