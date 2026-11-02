// =====================================================================
// Pruebas de los validadores de las 12 colecciones (tarea 1.3)
// tickets tiene su propio script: 07_tickets_pruebas.js
//
// Requisito: haber corrido antes los 12 scripts de database/validadores/.
// El script copia cada validador de helpdesk a una base temporal
// helpdesk_pruebas, inserta los documentos de prueba y al final borra esa
// base. No toca los datos de helpdesk.
//
// Resultado esperado al final: 23 aceptados, 46 rechazados y
// 69/69 casos con el resultado esperado.
// =====================================================================

(function () {
  const COLECCIONES = ["empresas", "roles", "usuarios", "planes", "membresias", "pagos",
    "comentariosTicket", "articulos", "capacitaciones", "solicitudesCapacitacion",
    "inscripciones", "refreshTokens"];

  const helpdesk = db.getSiblingDB("helpdesk");
  const pruebas = db.getSiblingDB("helpdesk_pruebas");
  pruebas.dropDatabase();

  for (const nombre of COLECCIONES) {
    const info = helpdesk.getCollectionInfos({ name: nombre })[0];
    if (!info || !info.options.validator) {
      print("Falta el validador de helpdesk." + nombre + ". Corre primero su script.");
      return;
    }
    pruebas.createCollection(nombre, {
      validator: info.options.validator,
      validationLevel: "strict",
      validationAction: "error"
    });
  }

  const ahora = new Date();
  const comunes = () => ({ schemaVersion: 1, fechaCreacion: ahora, fechaActualizacion: ahora });
  const con = (base, cambios) => Object.assign(base(), cambios);
  const sin = (base, campo) => { const d = base(); delete d[campo]; return d; };
  const id = () => new ObjectId();

  // Hash BCrypt de ejemplo con el formato correcto (60 caracteres)
  const HASH = "$2a$10$" + "N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
  const vector = (n) => Array.from({ length: n }, (_, i) => (i + 0.5) / 1000);

  // ---------- Documentos base válidos ----------

  const empresa = () => Object.assign(comunes(), {
    nit: "900123456-1",
    razonSocial: "Lácteos del Zulia S.A.S.",
    contacto: {
      nombre: "Yeimy Carrascal",
      email: "compras@lacteoszulia.co",
      telefono: "+57 607 5712345",
      direccion: "Av. 0 # 12-45",
      ciudad: "Cúcuta"
    }
  });

  const rol = () => Object.assign(comunes(), {
    nombre: "CLIENTE",
    descripcion: "Usuario final de la empresa cliente",
    alcance: "EMPRESA",
    permisos: ["MEMBRESIAS_VER", "TICKETS_CREAR", "TICKETS_VER_PROPIOS"],
    activo: true,
    esSistema: true
  });

  const usuario = () => Object.assign(comunes(), {
    nombre: "Camilo Quintero",
    email: "camilo.quintero@lacteoszulia.co",
    passwordHash: HASH,
    rolIds: [id()],
    empresaId: id(),
    activo: true
  });

  const plan = () => Object.assign(comunes(), {
    nombre: "Estándar",
    descripcion: "Respuesta en 24 horas, 3 módulos",
    precio: NumberDecimal("450000.00"),
    nivelAcceso: "ESTANDAR",
    limites: { horasRespuesta: 24, capacitacionesIncluidas: 2, maxUsuarios: 15, maxModulos: 3 },
    activo: true
  });

  const membresia = () => Object.assign(comunes(), {
    empresaId: id(),
    planId: id(),
    modulosContratados: ["INVENTARIOS", "VENTAS_EN_SITIO"],
    fechaInicio: ahora,
    fechaVencimiento: new Date(ahora.getTime() + 365 * 24 * 3600 * 1000),
    estado: "PENDIENTE"
  });

  const pago = () => Object.assign(comunes(), {
    membresiaId: id(),
    empresaId: id(),
    monto: NumberDecimal("450000.00"),
    fechaPago: ahora,
    metodoPago: "PSE",
    referencia: "PSE-20261102-0042",
    resultado: "APROBADO",
    registradoPorId: id()
  });

  const comentario = () => Object.assign(comunes(), {
    ticketId: id(),
    autorId: id(),
    texto: "Reinicié el datáfono y sigue sin conectar.",
    editado: false
  });

  const articulo = () => Object.assign(comunes(), {
    titulo: "Datáfono sin conexión en caja",
    contenido: "Verificar la red del punto de venta y reiniciar el servicio de pagos.",
    modulo: "VENTAS_EN_SITIO",
    etiquetas: ["datafono", "caja"],
    visibilidad: "INTERNO",
    ticketIds: [id()],
    autorId: id(),
    embedding: vector(768),
    embeddingModelo: "gemini-embedding-001/768"
  });

  const capMasiva = () => Object.assign(comunes(), {
    titulo: "Cierre de caja en Ventas en Sitio",
    tipo: "MASIVA",
    modulo: "VENTAS_EN_SITIO",
    empresaId: null,
    fechaInicio: ahora,
    duracionMinutos: 90,
    fechaFin: new Date(ahora.getTime() + 90 * 60 * 1000),
    cupo: 40,
    cuposOcupados: 0,
    instructorId: id(),
    estado: "PROGRAMADA",
    solicitudId: null
  });

  const capPersonalizada = () => Object.assign(capMasiva(), {
    tipo: "PERSONALIZADA",
    empresaId: id(),
    solicitudId: id(),
    cupo: 8
  });

  const solicitudPendiente = () => Object.assign(comunes(), {
    empresaId: id(),
    solicitanteId: id(),
    modulo: "PRODUCCION",
    descripcion: "El equipo de planta no registra bien los lotes.",
    participantesEstimados: 6,
    fechaPreferida: ahora,
    estado: "PENDIENTE",
    capacitacionId: null,
    motivoRechazo: null,
    atendidaPorId: null
  });

  const inscripcion = () => Object.assign(comunes(), {
    capacitacionId: id(),
    usuarioId: id(),
    empresaId: id(),
    asistio: null,
    marcadoPorId: null,
    fechaMarcado: null
  });

  const token = () => Object.assign(comunes(), {
    usuarioId: id(),
    tokenHash: "3f1c9a7e0b5d4e2f8a6c1b9d7e3f5a2c4b6d8e0f1a3c5e7b9d2f4a6c8e0b1d3f",
    expiraEn: new Date(ahora.getTime() + 7 * 24 * 3600 * 1000)
  });

  // ---------- Casos: [colección, nombre, debeAceptarse, documento] ----------

  const casos = [
    // empresas
    ["empresas", "completa", true, empresa()],
    ["empresas", "sin dirección ni ciudad", true,
      (() => { const d = empresa(); delete d.contacto.direccion; delete d.contacto.ciudad; return d; })()],
    ["empresas", "contacto sin email", false,
      (() => { const d = empresa(); delete d.contacto.email; return d; })()],
    ["empresas", "NIT sin dígito de verificación", false, con(empresa, { nit: "900123456" })],
    ["empresas", "email de contacto inválido", false,
      (() => { const d = empresa(); d.contacto.email = "compras.lacteoszulia.co"; return d; })()],
    ["empresas", "campo extra en contacto", false,
      (() => { const d = empresa(); d.contacto.cargo = "Jefe de compras"; return d; })()],
    ["empresas", "con plan guardado en la empresa", false, con(empresa, { planId: id() })],

    // roles
    ["roles", "rol de sistema", true, rol()],
    ["roles", "los 49 permisos", true,
      con(rol, { nombre: "ADMIN", alcance: "GLOBAL",
        permisos: helpdesk.getCollectionInfos({ name: "roles" })[0]
          .options.validator.$jsonSchema.properties.permisos.items.enum })],
    ["roles", "permiso fuera del catálogo", false, con(rol, { permisos: ["TICKETS_BORRAR_TODO"] })],
    ["roles", "permiso repetido", false, con(rol, { permisos: ["TICKETS_CREAR", "TICKETS_CREAR"] })],
    ["roles", "sin permisos", false, con(rol, { permisos: [] })],
    ["roles", "alcance inválido", false, con(rol, { alcance: "LOCAL" })],
    ["roles", "sin esSistema", false, sin(rol, "esSistema")],

    // usuarios
    ["usuarios", "de empresa", true, usuario()],
    ["usuarios", "global con empresaId null", true, con(usuario, { empresaId: null })],
    ["usuarios", "email con mayúsculas", false, con(usuario, { email: "Camilo.Quintero@lacteoszulia.co" })],
    ["usuarios", "contraseña en texto plano", false, con(usuario, { passwordHash: "Clave2026*" })],
    ["usuarios", "sin roles", false, con(usuario, { rolIds: [] })],
    ["usuarios", "11 roles", false, con(usuario, { rolIds: Array.from({ length: 11 }, id) })],
    ["usuarios", "sin empresaId (debe venir null explícito)", false, sin(usuario, "empresaId")],

    // planes
    ["planes", "completo", true, plan()],
    ["planes", "precio cero", false, con(plan, { precio: NumberDecimal("0") })],
    ["planes", "precio como double", false, con(plan, { precio: 450000.5 })],
    ["planes", "maxModulos 6", false,
      con(plan, { limites: { horasRespuesta: 24, capacitacionesIncluidas: 2, maxUsuarios: 15, maxModulos: 6 } })],
    ["planes", "límites sin horasRespuesta", false,
      con(plan, { limites: { capacitacionesIncluidas: 2, maxUsuarios: 15, maxModulos: 3 } })],
    ["planes", "nivel inválido", false, con(plan, { nivelAcceso: "ORO" })],

    // membresias
    ["membresias", "pendiente con 2 módulos", true, membresia()],
    ["membresias", "módulo repetido", false, con(membresia, { modulosContratados: ["COMPRAS", "COMPRAS"] })],
    ["membresias", "sin módulos", false, con(membresia, { modulosContratados: [] })],
    ["membresias", "módulo inexistente", false, con(membresia, { modulosContratados: ["NOMINA"] })],
    ["membresias", "estado VENCIDA guardado", false, con(membresia, { estado: "VENCIDA" })],

    // pagos
    ["pagos", "aprobado por PSE", true, pago()],
    ["pagos", "fallido por tarjeta", true, con(pago, { resultado: "FALLIDO", metodoPago: "TARJETA" })],
    ["pagos", "método fuera de la lista", false, con(pago, { metodoPago: "NEQUI" })],
    ["pagos", "monto negativo", false, con(pago, { monto: NumberDecimal("-1000") })],
    ["pagos", "resultado PENDIENTE", false, con(pago, { resultado: "PENDIENTE" })],
    ["pagos", "sin referencia", false, sin(pago, "referencia")],

    // comentariosTicket
    ["comentariosTicket", "comentario normal", true, comentario()],
    ["comentariosTicket", "texto solo con espacios", false, con(comentario, { texto: "   " })],
    ["comentariosTicket", "texto de 5001 caracteres", false, con(comentario, { texto: "x".repeat(5001) })],
    ["comentariosTicket", "sin editado", false, sin(comentario, "editado")],

    // articulos
    ["articulos", "con embedding de 768", true, articulo()],
    ["articulos", "embedding pendiente", true, con(articulo, { embedding: null, embeddingModelo: null })],
    ["articulos", "embedding de 767 valores", false, con(articulo, { embedding: vector(767) })],
    ["articulos", "embedding null con modelo", false, con(articulo, { embedding: null })],
    ["articulos", "vector sin modelo", false, con(articulo, { embeddingModelo: null })],
    ["articulos", "visibilidad inválida", false, con(articulo, { visibilidad: "PRIVADO" })],
    ["articulos", "21 etiquetas", false,
      con(articulo, { etiquetas: Array.from({ length: 21 }, (_, i) => "etiqueta" + i) })],

    // capacitaciones
    ["capacitaciones", "masiva", true, capMasiva()],
    ["capacitaciones", "personalizada con solicitud", true, capPersonalizada()],
    ["capacitaciones", "personalizada sin solicitud", true, con(capPersonalizada, { solicitudId: null })],
    ["capacitaciones", "personalizada sin empresa", false, con(capPersonalizada, { empresaId: null })],
    ["capacitaciones", "masiva con empresa", false, con(capMasiva, { empresaId: id() })],
    ["capacitaciones", "cupo cero", false, con(capMasiva, { cupo: 0 })],
    ["capacitaciones", "estado REALIZADA guardado", false, con(capMasiva, { estado: "REALIZADA" })],

    // solicitudesCapacitacion
    ["solicitudesCapacitacion", "pendiente", true, solicitudPendiente()],
    ["solicitudesCapacitacion", "programada", true,
      con(solicitudPendiente, { estado: "PROGRAMADA", capacitacionId: id(), atendidaPorId: id() })],
    ["solicitudesCapacitacion", "rechazada", true,
      con(solicitudPendiente, { estado: "RECHAZADA", motivoRechazo: "El plan no incluye más capacitaciones.", atendidaPorId: id() })],
    ["solicitudesCapacitacion", "devuelta a pendiente tras HU36", true,
      con(solicitudPendiente, { atendidaPorId: id() })],
    ["solicitudesCapacitacion", "rechazada sin motivo", false,
      con(solicitudPendiente, { estado: "RECHAZADA", atendidaPorId: id() })],
    ["solicitudesCapacitacion", "programada sin capacitación", false,
      con(solicitudPendiente, { estado: "PROGRAMADA", atendidaPorId: id() })],
    ["solicitudesCapacitacion", "cero participantes", false, con(solicitudPendiente, { participantesEstimados: 0 })],

    // inscripciones
    ["inscripciones", "sin marcar", true, inscripcion()],
    ["inscripciones", "marcada como asistió", true,
      con(inscripcion, { asistio: true, marcadoPorId: id(), fechaMarcado: ahora })],
    ["inscripciones", "asistió sin quién marcó", false, con(inscripcion, { asistio: true })],
    ["inscripciones", "asistio como texto", false,
      con(inscripcion, { asistio: "SI", marcadoPorId: id(), fechaMarcado: ahora })],

    // refreshTokens
    ["refreshTokens", "token vigente", true, token()],
    ["refreshTokens", "expiraEn como texto", false, con(token, { expiraEn: "2026-11-09" })]
  ];

  let correctos = 0;
  let aceptados = 0;
  for (const [coleccion, nombre, debeAceptarse, doc] of casos) {
    let aceptado = true;
    let motivo = "";
    try {
      pruebas.getCollection(coleccion).insertOne(doc);
      aceptados++;
    } catch (e) {
      aceptado = false;
      motivo = e.code === 121 ? "DocumentValidationFailure" : (e.code + " " + e.message);
    }
    const ok = aceptado === debeAceptarse;
    if (ok) correctos++;
    print((ok ? "OK    " : "FALLA ") + coleccion + " · " + nombre + " · " +
      (aceptado ? "aceptado" : "rechazado") + (motivo ? " (" + motivo + ")" : ""));
  }

  print("\n" + aceptados + " aceptados, " + (casos.length - aceptados) + " rechazados.");
  print(correctos + "/" + casos.length + " casos con el resultado esperado.");
  pruebas.dropDatabase();
})();
