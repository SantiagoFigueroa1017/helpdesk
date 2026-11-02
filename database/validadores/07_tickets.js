// =====================================================================
// Validador $jsonSchema de la colección tickets (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 7
//
// Cómo se ejecuta:
//   - Consola mongosh de Compass: pegar el archivo completo y Enter.
//   - Terminal: mongosh "<cadena>" --file database/validadores/07_tickets.js
//
// Si la colección no existe, la crea con el validador. Si ya existe,
// le reemplaza el validador con collMod. Se puede correr varias veces.
//
// Polimorfismo: oneOf de 6 ramas.
//   Ramas 1 a 5: tipo INCIDENTE, una por módulo, cada una con su detalle.
//   Rama 6: tipo SUGERENCIA, prohíbe el campo detalle.
// Las ramas se excluyen entre sí porque cada una fija tipo y módulo, así
// que un documento válido cumple exactamente una.
//
// _class: ningún nivel lo admite (decisión de 2.3). El backend desactiva
// el campo con DefaultMongoTypeMapper(null) y resuelve el tipo del
// detalle con modulo, no con el nombre de una clase Java.
//
// Lo que este validador no revisa (lo hace el backend):
//   - que solicitanteId pertenezca a empresaId y que el módulo esté contratado
//   - que fechaLimiteRespuesta = fechaCreacion + horas del plan
//   - las transiciones de estado y quién puede hacerlas
//   $jsonSchema no compara campos entre sí ni consulta otras colecciones.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");

  // ---------- Piezas reutilizables ----------

  // Texto obligatorio: al menos un carácter que no sea espacio.
  const texto = (max) => ({
    bsonType: "string",
    minLength: 1,
    maxLength: max,
    pattern: "\\S"
  });

  // Códigos e identificadores externos (bodega, lote, caja, NIT, ...).
  // Longitud aprobada en el chat 1.3: 50 caracteres.
  const codigo = texto(50);

  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];
  const ESTADOS = ["ABIERTO", "EN_PROCESO", "RESUELTO", "CERRADO"];

  // Rama de incidente: fija tipo y módulo, exige detalle con su forma.
  const ramaIncidente = (modulo, propiedades, obligatorios) => ({
    title: "INCIDENTE de " + modulo,
    required: ["detalle"],
    properties: {
      tipo: { enum: ["INCIDENTE"] },
      modulo: { enum: [modulo] },
      detalle: {
        bsonType: "object",
        required: obligatorios,
        additionalProperties: false,
        properties: propiedades
      }
    }
  });

  // ---------- Detalle por módulo ----------

  const ventasOnline = ramaIncidente("VENTAS_ONLINE", {
    pasarelaPagoId: codigo,
    urlError: texto(2048),
    navegador: texto(100),
    logApi: { bsonType: "string", maxLength: 20000 }
  }, ["pasarelaPagoId", "urlError", "navegador"]);

  const produccion = ramaIncidente("PRODUCCION", {
    maquinariaId: codigo,
    codigoLote: codigo,
    faseProceso: {
      enum: ["PREPARACION", "MEZCLADO", "TRANSFORMACION", "CONTROL_CALIDAD", "EMPAQUE", "ALMACENAMIENTO"]
    },
    // double exacto. Desde mongosh un entero como 78 se guarda como int,
    // por eso en las pruebas se escribe Double(78) o 78.5.
    temperaturaSensor: { bsonType: "double" }
  }, ["maquinariaId", "codigoLote", "faseProceso"]);

  const inventarios = ramaIncidente("INVENTARIOS", {
    codigoBodega: codigo,
    codigoProducto: codigo,
    tipoOperacion: { enum: ["ENTRADA", "SALIDA", "TRASLADO", "AJUSTE", "ALERTA_STOCK"] },
    codigoBarras: codigo,
    existenciaSistema: { bsonType: "int" }
  }, ["codigoBodega", "codigoProducto", "tipoOperacion"]);

  const compras = ramaIncidente("COMPRAS", {
    nitProveedor: codigo,
    etapa: { enum: ["ORDEN_COMPRA", "RECEPCION", "CUENTA_POR_PAGAR", "PAGO_PROVEEDOR"] },
    numeroOrdenCompra: codigo,
    numeroFacturaProveedor: codigo,
    montoInvolucrado: { bsonType: "decimal" }
  }, ["nitProveedor", "etapa"]);

  const ventasEnSitio = ramaIncidente("VENTAS_EN_SITIO", {
    cajaId: codigo,
    turnoId: codigo,
    dispositivo: { enum: ["IMPRESORA", "LECTOR_CODIGO", "CAJON_MONEDERO", "DATAFONO", "NINGUNO"] },
    numeroFactura: codigo,
    medioPago: { enum: ["EFECTIVO", "TARJETA", "TRANSFERENCIA", "MIXTO"] }
  }, ["cajaId", "turnoId", "dispositivo"]);

  const sugerencia = {
    title: "SUGERENCIA de cualquier módulo, sin detalle",
    properties: { tipo: { enum: ["SUGERENCIA"] } },
    not: { required: ["detalle"] }
  };

  // ---------- Esquema completo ----------

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: "tickets",
      required: [
        "schemaVersion", "fechaCreacion", "fechaActualizacion",
        "empresaId", "creadorId", "solicitanteId", "agenteId",
        "modulo", "tipo", "titulo", "descripcion", "estado",
        "historialEstados", "fechaResolucion", "fechaLimiteRespuesta",
        "fechaPrimeraAsignacion"
      ],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        empresaId: { bsonType: "objectId" },
        creadorId: { bsonType: "objectId" },
        solicitanteId: { bsonType: "objectId" },
        agenteId: { bsonType: ["objectId", "null"] },

        modulo: { enum: MODULOS },
        tipo: { enum: ["INCIDENTE", "SUGERENCIA"] },
        titulo: texto(150),
        descripcion: texto(5000),
        detalle: { bsonType: "object" },   // la forma exacta la da la rama del oneOf

        estado: { enum: ESTADOS },
        historialEstados: {
          bsonType: "array",
          minItems: 1,
          maxItems: 50,
          items: {
            bsonType: "object",
            required: ["estado", "fecha", "usuarioId"],
            additionalProperties: false,
            properties: {
              estado: { enum: ESTADOS },
              fecha: { bsonType: "date" },
              usuarioId: { bsonType: "objectId" }
            }
          }
        },

        fechaResolucion: { bsonType: ["date", "null"] },
        fechaLimiteRespuesta: { bsonType: "date" },
        fechaPrimeraAsignacion: { bsonType: ["date", "null"] }
      },
      oneOf: [ventasOnline, produccion, inventarios, compras, ventasEnSitio, sugerencia]
    }
  };

  const opciones = { validationLevel: "strict", validationAction: "error" };

  // ---------- Crear o actualizar ----------

  const existe = helpdesk.getCollectionNames().includes("tickets");
  try {
    if (existe) {
      helpdesk.runCommand(Object.assign({ collMod: "tickets", validator: validador }, opciones));
      print("tickets: validador actualizado con collMod.");
    } else {
      helpdesk.createCollection("tickets", Object.assign({ validator: validador }, opciones));
      print("tickets: colección creada con validador.");
    }
  } catch (e) {
    if (e.code === 13) {
      print("Sin permiso para collMod. El usuario necesita el rol dbAdmin sobre helpdesk.");
    }
    throw e;
  }

  const info = helpdesk.getCollectionInfos({ name: "tickets" })[0].options;
  print("validationLevel: " + info.validationLevel + " · validationAction: " + info.validationAction);
  print("Ramas del oneOf: " + info.validator.$jsonSchema.oneOf.length);
})();
