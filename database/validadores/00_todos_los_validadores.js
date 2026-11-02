// =====================================================================
// Los 13 validadores en un solo archivo, para pegar una vez en Compass.
// ARCHIVO GENERADO: no se edita a mano. Se regenera concatenando
// 01_empresas.js a 13_refreshTokens.js en orden.
// =====================================================================

// ---------- 01_empresas.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 02_roles.js ----------
// =====================================================================
// Validador $jsonSchema de la colección roles (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 2
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/02_roles.js
// Crea la colección o reemplaza su validador con collMod.
//
// permisos solo acepta los 49 códigos del catálogo. Si el catálogo cambia,
// hay que actualizar esta lista y volver a correr el script.
//
// El backend revisa: que no cambie el alcance con usuarios asignados,
// que los roles esSistema no cambien nombre ni alcance, y que ADMIN
// conserve ROLES_GESTIONAR (HU27).
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "roles";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const PERMISOS = [
    "EMPRESAS_CREAR", "EMPRESAS_EDITAR", "EMPRESAS_ELIMINAR", "EMPRESAS_EDITAR_CONTACTO", "EMPRESAS_VER",
    "USUARIOS_CREAR", "USUARIOS_EDITAR", "USUARIOS_ELIMINAR", "USUARIOS_VER",
    "PLANES_CREAR", "PLANES_EDITAR", "PLANES_ELIMINAR", "PLANES_VER",
    "MEMBRESIAS_CREAR", "MEMBRESIAS_EDITAR", "MEMBRESIAS_ELIMINAR", "MEMBRESIAS_VER",
    "PAGOS_REGISTRAR", "PAGOS_EDITAR", "PAGOS_ELIMINAR", "PAGOS_VER",
    "TICKETS_CREAR", "TICKETS_VER", "TICKETS_VER_PROPIOS", "TICKETS_EDITAR", "TICKETS_EDITAR_PROPIOS",
    "TICKETS_ASIGNAR", "TICKETS_ASIGNAR_OTROS", "TICKETS_ELIMINAR",
    "COMENTARIOS_CREAR", "COMENTARIOS_ELIMINAR",
    "ROLES_VER", "ROLES_GESTIONAR",
    "ARTICULOS_VER", "ARTICULOS_VER_INTERNOS", "ARTICULOS_CREAR", "ARTICULOS_EDITAR", "ARTICULOS_ELIMINAR",
    "CAPACITACIONES_VER", "CAPACITACIONES_CREAR", "CAPACITACIONES_EDITAR", "CAPACITACIONES_ELIMINAR",
    "CAPACITACIONES_SOLICITAR",
    "SOLICITUDES_GESTIONAR",
    "INSCRIPCIONES_PROPIAS", "INSCRIPCIONES_GESTIONAR", "INSCRIPCIONES_VER",
    "ASISTENCIA_MARCAR",
    "REPORTES_VER"
  ];
  if (PERMISOS.length !== 49) throw new Error("El catálogo debe tener 49 permisos y tiene " + PERMISOS.length);

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "nombre", "descripcion", "alcance", "permisos", "activo", "esSistema"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        nombre: texto(50),
        descripcion: texto(500),
        alcance: { enum: ["GLOBAL", "EMPRESA"] },
        permisos: {
          bsonType: "array",
          minItems: 1,
          maxItems: PERMISOS.length,
          uniqueItems: true,
          items: { enum: PERMISOS }
        },
        activo: { bsonType: "bool" },
        esSistema: { bsonType: "bool" }
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

// ---------- 03_usuarios.js ----------
// =====================================================================
// Validador $jsonSchema de la colección usuarios (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 3
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/03_usuarios.js
// Crea la colección o reemplaza su validador con collMod.
//
// email solo acepta minúsculas: el backend lo normaliza antes de guardar.
// passwordHash solo acepta el formato BCrypt de 60 caracteres que produce
// BCryptPasswordEncoder ($2a$10$...). Una contraseña en texto plano no entra.
//
// El backend revisa: que todos los roles tengan el mismo alcance, que
// empresaId sea null con roles GLOBAL y no null con roles EMPRESA, y el
// límite maxUsuarios del plan.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "usuarios";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "nombre", "email", "passwordHash", "rolIds", "empresaId", "activo"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        nombre: texto(150),
        email: { bsonType: "string", maxLength: 254, pattern: "^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$" },
        passwordHash: { bsonType: "string", pattern: "^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$" },
        rolIds: {
          bsonType: "array",
          minItems: 1,
          maxItems: 10,
          uniqueItems: true,
          items: { bsonType: "objectId" }
        },
        empresaId: { bsonType: ["objectId", "null"] },
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

// ---------- 04_planes.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 05_membresias.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 06_pagos.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 07_tickets.js ----------
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
        // Spring Data MongoDB escribe _class si no se desactiva. Se decide en 2.3.
        _class: { bsonType: "string" },
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

// ---------- 08_comentariosTicket.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 09_articulos.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 10_capacitaciones.js ----------
// =====================================================================
// Validador $jsonSchema de la colección capacitaciones (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 10
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/10_capacitaciones.js
// Crea la colección o reemplaza su validador con collMod.
//
// oneOf por tipo:
//   - MASIVA: empresaId y solicitudId en null
//   - PERSONALIZADA: empresaId obligatorio; solicitudId puede ser null
//     si el agente la crea sin solicitud previa
//
// El backend revisa: fechaInicio no anterior a hoy, fechaFin = inicio +
// duración, cupo no menor que cuposOcupados (HU35), cuposOcupados no mayor
// que cupo al inscribir (transacción de HU37) e instructor de alcance GLOBAL.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "capacitaciones";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });
  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "titulo", "tipo", "modulo", "empresaId", "fechaInicio", "duracionMinutos", "fechaFin",
        "cupo", "cuposOcupados", "instructorId", "estado", "solicitudId"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        titulo: texto(200),
        tipo: { enum: ["MASIVA", "PERSONALIZADA"] },
        modulo: { enum: MODULOS },
        empresaId: { bsonType: ["objectId", "null"] },
        fechaInicio: { bsonType: "date" },
        duracionMinutos: { bsonType: "int", minimum: 1, maximum: 1440 },
        fechaFin: { bsonType: "date" },
        cupo: { bsonType: "int", minimum: 1 },
        cuposOcupados: { bsonType: "int", minimum: 0 },
        instructorId: { bsonType: "objectId" },
        estado: { enum: ["PROGRAMADA", "CANCELADA"] },
        solicitudId: { bsonType: ["objectId", "null"] }
      },
      oneOf: [
        {
          title: "MASIVA",
          properties: {
            tipo: { enum: ["MASIVA"] },
            empresaId: { bsonType: "null" },
            solicitudId: { bsonType: "null" }
          }
        },
        {
          title: "PERSONALIZADA",
          properties: {
            tipo: { enum: ["PERSONALIZADA"] },
            empresaId: { bsonType: "objectId" }
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

// ---------- 11_solicitudesCapacitacion.js ----------
// =====================================================================
// Validador $jsonSchema de la colección solicitudesCapacitacion (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 11
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/11_solicitudesCapacitacion.js
// Crea la colección o reemplaza su validador con collMod.
//
// oneOf por estado:
//   - PENDIENTE: sin capacitación y sin motivo. atendidaPorId puede tener
//     valor porque una solicitud vuelve a PENDIENTE si se borra su
//     capacitación (HU36 v.4) y conserva quién la había programado.
//   - PROGRAMADA: capacitacionId y atendidaPorId obligatorios, sin motivo.
//   - RECHAZADA: motivoRechazo y atendidaPorId obligatorios, sin capacitación.
//
// El backend revisa: membresía con soporte al crear y el límite
// capacitacionesIncluidas del plan.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "solicitudesCapacitacion";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });
  const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "empresaId", "solicitanteId", "modulo", "descripcion", "participantesEstimados",
        "estado", "capacitacionId", "motivoRechazo", "atendidaPorId"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        empresaId: { bsonType: "objectId" },
        solicitanteId: { bsonType: "objectId" },
        modulo: { enum: MODULOS },
        descripcion: texto(2000),
        participantesEstimados: { bsonType: "int", minimum: 1 },
        fechaPreferida: { bsonType: "date" },
        estado: { enum: ["PENDIENTE", "PROGRAMADA", "RECHAZADA"] },
        capacitacionId: { bsonType: ["objectId", "null"] },
        motivoRechazo: { bsonType: ["string", "null"] },
        atendidaPorId: { bsonType: ["objectId", "null"] }
      },
      oneOf: [
        {
          title: "PENDIENTE",
          properties: {
            estado: { enum: ["PENDIENTE"] },
            capacitacionId: { bsonType: "null" },
            motivoRechazo: { bsonType: "null" }
          }
        },
        {
          title: "PROGRAMADA",
          properties: {
            estado: { enum: ["PROGRAMADA"] },
            capacitacionId: { bsonType: "objectId" },
            motivoRechazo: { bsonType: "null" },
            atendidaPorId: { bsonType: "objectId" }
          }
        },
        {
          title: "RECHAZADA",
          properties: {
            estado: { enum: ["RECHAZADA"] },
            capacitacionId: { bsonType: "null" },
            motivoRechazo: texto(1000),
            atendidaPorId: { bsonType: "objectId" }
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

// ---------- 12_inscripciones.js ----------
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
        _class: { bsonType: "string" },
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

// ---------- 13_refreshTokens.js ----------
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
