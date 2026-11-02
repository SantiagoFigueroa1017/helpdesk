// =====================================================================
// 00_datos_semilla.js · Datos de prueba de la base helpdesk (tarea 1.5)
//
// Carga datos ficticios en las 13 colecciones para que explain() muestre
// la diferencia entre usar un índice y recorrer la colección, y para que
// los pipelines A1 a A9 de la tarea 1.6 tengan qué contar.
//
// Cómo se usa: pegar el archivo completo en la consola mongosh de Compass,
// conectada a Atlas o a local, DESPUÉS de los validadores y los índices.
// Tarda de 1 a 3 minutos en Atlas M0.
//
// ATENCIÓN: borra todos los documentos de las 13 colecciones de helpdesk
// antes de cargar. No toca validadores ni índices. Se puede correr varias
// veces y siempre deja los mismos datos (generador con semilla fija), salvo
// refreshTokens, que usan la fecha del momento de la carga.
//
// Decisiones del chat 1.5:
//   - 40 empresas y 8.000 tickets.
//   - Historia de noviembre de 2025 a octubre de 2026. Fecha de corte:
//     2026-11-01 12:00 UTC. Ningún evento queda después del corte, salvo
//     las capacitaciones programadas para noviembre y diciembre de 2026.
//   - Artículos: la mitad con embedding null y la mitad con vectores
//     sintéticos (embeddingModelo "sintetico/768"). En la 2.7 el backend
//     los regenera con Gemini porque el modelo no coincide.
//   - Todos los usuarios tienen la contraseña de demo Helpdesk2026*
//     (hash BCrypt de 60 caracteres, costo 10).
//
// Todo cumple los validadores de 1.3 y además las reglas que revisa el
// backend: módulo contratado, solicitante de la empresa, soporte vigente
// al crear, cupos = inscripciones, límites del plan, etc.
// =====================================================================

(() => {

const BD = "helpdesk";
const base = db.getSiblingDB(BD);
const inicioCarga = Date.now();

// ---------------------------------------------------------------------
// 0. Comprobaciones previas
// ---------------------------------------------------------------------
const COLECCIONES = ["roles", "planes", "empresas", "usuarios", "membresias", "pagos",
  "solicitudesCapacitacion", "capacitaciones", "inscripciones", "tickets",
  "comentariosTicket", "articulos", "refreshTokens"];

const existentes = base.getCollectionNames();
const faltan = COLECCIONES.filter(c => !existentes.includes(c));
if (faltan.length) {
  print("No existen estas colecciones: " + faltan.join(", "));
  print("Corre primero validadores/00_todos_los_validadores.js y indices/00_indices.js.");
  return;
}
const sinValidador = COLECCIONES.filter(c => {
  const o = base.getCollectionInfos({ name: c })[0].options;
  return !o.validator || o.validationLevel !== "strict";
});
if (sinValidador.length) {
  print("Estas colecciones no tienen validador strict: " + sinValidador.join(", "));
  return;
}

// ---------------------------------------------------------------------
// 1. Utilidades
// ---------------------------------------------------------------------

// Generador pseudoaleatorio con semilla fija (mulberry32): mismos datos en cada carga.
let estado = 20261102;
const rnd = () => {
  estado = (estado + 0x6D2B79F5) | 0;
  let t = Math.imul(estado ^ (estado >>> 15), 1 | estado);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const entre = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const prob = p => rnd() < p;
const elegir = arr => arr[Math.floor(rnd() * arr.length)];
const elegirPeso = pares => {
  const total = pares.reduce((s, p) => s + p[1], 0);
  let r = rnd() * total;
  for (const [v, w] of pares) { r -= w; if (r < 0) return v; }
  return pares[pares.length - 1][0];
};
const mezclar = arr => {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
  return a;
};
const pad = (n, w) => String(n).padStart(w, "0");

const H = 3600 * 1000;
const D = 24 * H;
const fecha = (y, m, d, h = 14, min = 0) => new Date(Date.UTC(y, m - 1, d, h, min));
const masH = (f, horas) => new Date(f.getTime() + horas * H);
const masD = (f, dias) => new Date(f.getTime() + dias * D);
const menor = (a, b) => (a < b ? a : b);
const mayor = (a, b) => (a > b ? a : b);

const CORTE = fecha(2026, 11, 1, 12);       // fecha de corte de la historia
const INICIO_HISTORIA = fecha(2025, 11, 1, 5);
// Los 12 meses de la historia: [año, mes]
const MESES = [];
for (let i = 0; i < 12; i++) MESES.push([2025 + Math.floor((10 + i) / 12), ((10 + i) % 12) + 1]);

// ObjectId determinista: segundos de la fecha + código de colección + contador.
// Así el _id también queda ordenado por fecha de creación.
let contadorId = 0;
const oid = (f, codigo) => ObjectId(
  pad(Math.floor(f.getTime() / 1000).toString(16), 8) + codigo + pad((++contadorId).toString(16), 14));

const sinTildes = s => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
const slug = s => sinTildes(s).toLowerCase().replace(/[^a-z0-9]+/g, "");

// Dígito de verificación del NIT (algoritmo de la DIAN).
const PESOS_NIT = [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71];
const nitConDv = numero => {
  const s = String(numero);
  let suma = 0;
  for (let i = 0; i < s.length; i++) suma += Number(s[s.length - 1 - i]) * PESOS_NIT[i];
  const r = suma % 11;
  return s + "-" + (r > 1 ? 11 - r : r);
};

const comunes = f => ({ schemaVersion: 1, fechaCreacion: f, fechaActualizacion: f });

// Contraseña de demo: Helpdesk2026*
const PASSWORD_HASH = "$2a$10$sCZByTRVMGcQ4jrX46C6kuEKvM8HFOrfUkromHQfDsbbqpmFLwqt.";

const MODULOS = ["VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO"];
// Qué tanto falla cada módulo (A1 debe mostrar un orden claro).
const PESO_MODULO = { VENTAS_ONLINE: 30, VENTAS_EN_SITIO: 26, INVENTARIOS: 20, COMPRAS: 12, PRODUCCION: 12 };
// Horas base de resolución por módulo (A1: tiempo promedio).
const HORAS_RESOLUCION = { VENTAS_ONLINE: 18, VENTAS_EN_SITIO: 10, INVENTARIOS: 30, COMPRAS: 40, PRODUCCION: 55 };

// ---------------------------------------------------------------------
// 2. Roles (los 4 del sistema con la tabla del modelo, más 1 de ejemplo)
// ---------------------------------------------------------------------
const P = {
  ADMIN: [
    "EMPRESAS_CREAR", "EMPRESAS_EDITAR", "EMPRESAS_ELIMINAR", "EMPRESAS_VER",
    "USUARIOS_CREAR", "USUARIOS_EDITAR", "USUARIOS_ELIMINAR", "USUARIOS_VER",
    "PLANES_CREAR", "PLANES_EDITAR", "PLANES_ELIMINAR", "PLANES_VER",
    "MEMBRESIAS_CREAR", "MEMBRESIAS_EDITAR", "MEMBRESIAS_ELIMINAR", "MEMBRESIAS_VER",
    "PAGOS_REGISTRAR", "PAGOS_EDITAR", "PAGOS_ELIMINAR", "PAGOS_VER",
    "TICKETS_CREAR", "TICKETS_VER", "TICKETS_EDITAR", "TICKETS_ASIGNAR", "TICKETS_ASIGNAR_OTROS", "TICKETS_ELIMINAR",
    "COMENTARIOS_CREAR", "COMENTARIOS_ELIMINAR",
    "ROLES_VER", "ROLES_GESTIONAR",
    "ARTICULOS_VER", "ARTICULOS_VER_INTERNOS", "ARTICULOS_CREAR", "ARTICULOS_EDITAR", "ARTICULOS_ELIMINAR",
    "CAPACITACIONES_VER", "CAPACITACIONES_CREAR", "CAPACITACIONES_EDITAR", "CAPACITACIONES_ELIMINAR",
    "SOLICITUDES_GESTIONAR", "INSCRIPCIONES_GESTIONAR", "INSCRIPCIONES_VER", "ASISTENCIA_MARCAR",
    "REPORTES_VER"
  ],
  AGENTE_SOPORTE: [
    "EMPRESAS_VER", "USUARIOS_VER", "PLANES_VER", "MEMBRESIAS_VER",
    "TICKETS_CREAR", "TICKETS_VER", "TICKETS_EDITAR", "TICKETS_ASIGNAR",
    "COMENTARIOS_CREAR",
    "ARTICULOS_VER", "ARTICULOS_VER_INTERNOS", "ARTICULOS_CREAR", "ARTICULOS_EDITAR", "ARTICULOS_ELIMINAR",
    "CAPACITACIONES_VER", "CAPACITACIONES_CREAR", "CAPACITACIONES_EDITAR",
    "SOLICITUDES_GESTIONAR", "INSCRIPCIONES_GESTIONAR", "INSCRIPCIONES_VER", "ASISTENCIA_MARCAR",
    "REPORTES_VER"
  ],
  CLIENTE_ADMIN: [
    "EMPRESAS_EDITAR_CONTACTO", "EMPRESAS_VER",
    "USUARIOS_CREAR", "USUARIOS_EDITAR", "USUARIOS_ELIMINAR", "USUARIOS_VER",
    "PLANES_VER", "MEMBRESIAS_VER", "PAGOS_VER",
    "TICKETS_CREAR", "TICKETS_VER", "TICKETS_EDITAR_PROPIOS",
    "COMENTARIOS_CREAR", "ARTICULOS_VER", "CAPACITACIONES_VER", "CAPACITACIONES_SOLICITAR"
  ],
  CLIENTE: [
    "MEMBRESIAS_VER", "TICKETS_CREAR", "TICKETS_VER_PROPIOS", "TICKETS_EDITAR_PROPIOS",
    "COMENTARIOS_CREAR", "ARTICULOS_VER", "CAPACITACIONES_VER", "INSCRIPCIONES_PROPIAS"
  ]
};

const fRoles = fecha(2025, 7, 1, 13);
const roles = [
  { nombre: "ADMIN", descripcion: "Administración completa del sistema. Personal de Sistemas Integrados S.A.", alcance: "GLOBAL", permisos: P.ADMIN, esSistema: true },
  { nombre: "AGENTE_SOPORTE", descripcion: "Atiende tickets, mantiene la base de conocimientos, programa capacitaciones y atiende solicitudes.", alcance: "GLOBAL", permisos: P.AGENTE_SOPORTE, esSistema: true },
  { nombre: "CLIENTE_ADMIN", descripcion: "Administra los usuarios y el contacto de su empresa, consulta membresía y pagos, ve todos los tickets de su empresa y solicita capacitaciones personalizadas.", alcance: "EMPRESA", permisos: P.CLIENTE_ADMIN, esSistema: true },
  { nombre: "CLIENTE", descripcion: "Crea y sigue sus tickets, consulta la base de conocimientos y se inscribe a capacitaciones.", alcance: "EMPRESA", permisos: P.CLIENTE, esSistema: true },
  // Rol creado desde el sistema (HU25). Sin usuarios: sirve para probar HU27 y HU28.
  { nombre: "CONSULTA_REPORTES", descripcion: "Rol de ejemplo para gerencia: consulta empresas, tickets y reportes sin modificar nada.", alcance: "GLOBAL", permisos: ["EMPRESAS_VER", "TICKETS_VER", "REPORTES_VER"], esSistema: false }
].map(r => Object.assign({ _id: oid(fRoles, "01") }, comunes(fRoles), r, { activo: true }));
const ROL = Object.fromEntries(roles.map(r => [r.nombre, r._id]));

// ---------------------------------------------------------------------
// 3. Planes (precio = valor mensual de la membresía en pesos)
// ---------------------------------------------------------------------
const fPlanes = fecha(2025, 7, 1, 14);
const PLANES = {
  BASICO: { nombre: "Básico", descripcion: "Soporte por tickets con respuesta en 48 horas para empresas pequeñas.", precio: 450000, nivelAcceso: "BASICO", limites: { horasRespuesta: 48, capacitacionesIncluidas: 0, maxUsuarios: 8, maxModulos: 2 } },
  ESTANDAR: { nombre: "Estándar", descripcion: "Respuesta en 24 horas, hasta 3 módulos y 2 capacitaciones personalizadas por vigencia.", precio: 900000, nivelAcceso: "ESTANDAR", limites: { horasRespuesta: 24, capacitacionesIncluidas: 2, maxUsuarios: 15, maxModulos: 3 } },
  PREMIUM: { nombre: "Premium", descripcion: "Respuesta en 8 horas, los 5 módulos y 6 capacitaciones personalizadas por vigencia.", precio: 1800000, nivelAcceso: "PREMIUM", limites: { horasRespuesta: 8, capacitacionesIncluidas: 6, maxUsuarios: 30, maxModulos: 5 } },
  // Plan inactivo y sin membresías: se puede eliminar en la demo de HU12.
  EMPRENDEDOR: { nombre: "Emprendedor", descripcion: "Plan de lanzamiento retirado de la venta. Un módulo y respuesta en 72 horas.", precio: 250000, nivelAcceso: "BASICO", limites: { horasRespuesta: 72, capacitacionesIncluidas: 0, maxUsuarios: 4, maxModulos: 1 }, activo: false }
};
const planes = Object.entries(PLANES).map(([clave, p]) => {
  const doc = Object.assign({ _id: oid(fPlanes, "02") }, comunes(fPlanes), {
    nombre: p.nombre, descripcion: p.descripcion,
    precio: NumberDecimal(p.precio + ".00"),
    nivelAcceso: p.nivelAcceso, limites: p.limites,
    activo: p.activo !== false
  });
  PLANES[clave].doc = doc;
  return doc;
});
const FACTOR_PLAN = { BASICO: 1.3, ESTANDAR: 1.0, PREMIUM: 0.7 };

// ---------------------------------------------------------------------
// 4. Empresas y su situación de membresía
// ---------------------------------------------------------------------
// [razón social, ciudad, plan, situación]
// Situación: A = activa, S<mes> = suspendida desde ese mes de 2026,
// V = vencida el 2026-09-30, P = pendiente, N<mes> = activa desde ese mes de 2026,
// SIN = sin membresía.
const EMPRESAS = [
  ["Distribuidora Ropero Hermanos S.A.S.", "Ocaña", "PREMIUM", "A"],
  ["Panificadora La Ocañerita S.A.S.", "Ocaña", "PREMIUM", "A"],
  ["Lácteos del Catatumbo S.A.S.", "Tibú", "PREMIUM", "A"],
  ["Ferretería El Tornillo de Ábrego S.A.S.", "Ábrego", "ESTANDAR", "A"],
  ["Comercializadora Pacheco y Asociados S.A.S.", "Cúcuta", "PREMIUM", "A"],
  ["Confecciones Sanjuán S.A.S.", "Bucaramanga", "ESTANDAR", "A"],
  ["Droguería Santa Clara de Ocaña S.A.S.", "Ocaña", "BASICO", "A"],
  ["Agroindustrias La Playa S.A.S.", "La Playa de Belén", "PREMIUM", "A"],
  ["Supermercado El Llanito S.A.S.", "Ocaña", "ESTANDAR", "S7"],
  ["Distribuidora de Cebolla Ocañera S.A.S.", "Ocaña", "ESTANDAR", "A"],
  ["Café Especial Hacarí S.A.S.", "Hacarí", "BASICO", "A"],
  ["Muebles Ascanio S.A.S.", "Cúcuta", "ESTANDAR", "A"],
  ["Tienda Naturista Bienestar Total S.A.S.", "Ocaña", "BASICO", "A"],
  ["Plásticos del Norte S.A.S.", "Cúcuta", "PREMIUM", "V"],
  ["Calzado Carrascal S.A.S.", "Bucaramanga", "ESTANDAR", "A"],
  ["Autopartes Quintero S.A.S.", "Aguachica", "ESTANDAR", "A"],
  ["Papelería Universitaria Ocaña S.A.S.", "Ocaña", "BASICO", "S8"],
  ["Frigorífico Santander del Norte S.A.S.", "Cúcuta", "PREMIUM", "A"],
  ["Arepas Doña Lucía S.A.S.", "Ocaña", "BASICO", "A"],
  ["Electrodomésticos La Once S.A.S.", "Ocaña", "ESTANDAR", "A"],
  ["Distribuidora Avícola Convención S.A.S.", "Convención", "ESTANDAR", "A"],
  ["Bebidas Naturales Teorama S.A.S.", "Teorama", "BASICO", "A"],
  ["Materiales de Construcción Aguachica S.A.S.", "Aguachica", "ESTANDAR", "A"],
  ["Panadería y Pastelería Pamplonita S.A.S.", "Pamplona", "BASICO", "S9"],
  ["Textiles Cúcuta Moda S.A.S.", "Cúcuta", "ESTANDAR", "A"],
  ["Óptica Visión Clara S.A.S.", "Ocaña", "BASICO", "A"],
  ["Mercados Campesinos del Catatumbo S.A.S.", "El Tarra", "ESTANDAR", "A"],
  ["Productos Cárnicos El Tarra S.A.S.", "El Tarra", "ESTANDAR", "A"],
  ["Ferretería Industrial Bucaramanga S.A.S.", "Bucaramanga", "BASICO", "V"],
  ["Dulces Típicos de Ábrego S.A.S.", "Ábrego", "BASICO", "A"],
  ["Moto Repuestos Ocaña S.A.S.", "Ocaña", "ESTANDAR", "S10"],
  ["Distribuidora Farmacéutica del Norte S.A.S.", "Cúcuta", "PREMIUM", "A"],
  ["Heladería Polo Norte Ocaña S.A.S.", "Ocaña", "BASICO", "N2"],
  ["Concentrados Agropecuarios San Calixto S.A.S.", "San Calixto", "ESTANDAR", "N3"],
  ["Tecnología y Celulares La Primavera S.A.S.", "Ocaña", "BASICO", "S10"],
  ["Cosméticos Belleza Natural S.A.S.", "Bucaramanga", "ESTANDAR", "N5"],
  ["Jugos y Pulpas del Trópico S.A.S.", "Aguachica", "PREMIUM", "N6"],
  ["Mueblería El Carpintero S.A.S.", "Ocaña", "BASICO", "P"],
  // Sin usuarios ni membresía: se puede eliminar en la demo de HU04.
  ["Almacén Agrícola El Surco S.A.S.", "Ábrego", null, "SIN"],
  // Con CLIENTE_ADMIN y sin membresía: queda sin soporte hasta HU13.
  ["Restaurante Sabores de Mi Tierra S.A.S.", "Ocaña", null, "SIN"]
];

const NOMBRES = ["Camilo", "Laura", "Diego", "Yeimy", "Andrés", "Natalia", "Jhon", "Paola", "Wilmer", "Leidy",
  "Fabián", "Marcela", "Edwin", "Yuliana", "Óscar", "Diana", "Brayan", "Katherine", "Sergio", "Mónica",
  "Hernán", "Lorena", "Javier", "Erika", "Cristian", "Angie", "Mauricio", "Sandra", "Fredy", "Viviana",
  "Alexander", "Johana", "Rubén", "Daniela", "Germán", "Tatiana", "Iván", "Yurley", "Nelson", "Claudia"];
const APELLIDOS = ["Quintero", "Sanjuán", "Pacheco", "Carrascal", "Ropero", "Ascanio", "Bayona", "Jácome", "Navarro", "Contreras",
  "Lobo", "Pallares", "Arévalo", "Sepúlveda", "Vergel", "Rincón", "Torrado", "Guerrero", "Álvarez", "Peñaranda",
  "Sánchez", "Durán", "Ovallos", "Picón", "Trigos", "Coronel", "Manosalva", "Velásquez", "Claro", "Angarita",
  "Barbosa", "Rizo", "Melo", "Quintana", "Ballesteros", "Galvis", "Prada", "Ramírez", "Amaya", "Castro"];
const persona = () => `${elegir(NOMBRES)} ${elegir(APELLIDOS)} ${elegir(APELLIDOS)}`;

const DIRECCIONES = ["Calle", "Carrera", "Avenida", "Transversal", "Diagonal"];
const telefono = () => `+57 3${entre(0, 2)}${entre(0, 9)} ${entre(100, 999)} ${pad(entre(0, 9999), 4)}`;

const empresas = [];
const empInfo = [];   // datos de trabajo que no se guardan
const nitsUsados = new Set();

EMPRESAS.forEach(([razon, ciudad, planClave, situacion], i) => {
  // Fecha de inicio de la membresía según la situación.
  let inicioMembresia = null;
  if (situacion.startsWith("N")) inicioMembresia = fecha(2026, Number(situacion.slice(1)), entre(1, 20), 14);
  else if (situacion === "P") inicioMembresia = fecha(2026, 10, 20, 15);
  else if (situacion !== "SIN") inicioMembresia = fecha(2025, entre(8, 10), entre(1, 28), 14);

  const fEmpresa = inicioMembresia ? masD(inicioMembresia, -entre(2, 10)) : fecha(2026, 10, entre(22, 29), 15);
  let nit;
  do { nit = nitConDv(entre(800000000, 901999999)); } while (nitsUsados.has(nit));
  nitsUsados.add(nit);

  const palabras = sinTildes(razon).replace(/ S\.A\.S\.$/, "").split(/\s+/).filter(p => !["de", "del", "la", "el", "y", "las", "los"].includes(p.toLowerCase()));
  const dominio = slug(palabras.slice(0, 2).join("")) + ".com.co";
  const contactoNombre = persona();
  const [n1, a1] = contactoNombre.split(" ");

  const doc = Object.assign({ _id: oid(fEmpresa, "03") }, comunes(fEmpresa), {
    nit,
    razonSocial: razon,
    contacto: {
      nombre: contactoNombre,
      email: `${slug(n1)}.${slug(a1)}@${dominio}`,
      telefono: telefono(),
      direccion: `${elegir(DIRECCIONES)} ${entre(1, 40)} # ${entre(1, 60)}-${entre(1, 99)}`,
      ciudad
    }
  });
  empresas.push(doc);
  empInfo.push({ i, doc, dominio, planClave, situacion, inicioMembresia, usuarios: [], clientes: [] });
});

// ---------------------------------------------------------------------
// 5. Usuarios
// ---------------------------------------------------------------------
const usuarios = [];
const emailsUsados = new Set();
const emailUnico = (nombre, dominio) => {
  const [n, a] = nombre.split(" ");
  let base1 = `${slug(n)}.${slug(a)}`, e = `${base1}@${dominio}`, k = 2;
  while (emailsUsados.has(e)) e = `${base1}${k++}@${dominio}`;
  emailsUsados.add(e);
  return e;
};
const nuevoUsuario = (nombre, dominio, rolIds, empresaId, f, extra = {}) => {
  const doc = Object.assign({ _id: oid(f, "04") }, comunes(f), {
    nombre, email: emailUnico(nombre, dominio), passwordHash: PASSWORD_HASH,
    rolIds, empresaId, activo: extra.activo !== false
  });
  if (extra.fechaActualizacion) doc.fechaActualizacion = extra.fechaActualizacion;
  usuarios.push(doc);
  return doc;
};

// Personal de Sistemas Integrados (alcance GLOBAL)
const DOM_GLOBAL = "sistemasintegrados.com.co";
const fPersonal = fecha(2025, 7, 15, 13);
const admin1 = nuevoUsuario("Liliana Contreras Bayona", DOM_GLOBAL, [ROL.ADMIN], null, fPersonal);
// Usuario con dos roles del mismo alcance (P21)
const admin2 = nuevoUsuario("Hernán Jácome Navarro", DOM_GLOBAL, [ROL.ADMIN, ROL.AGENTE_SOPORTE], null, fPersonal);
const NOMBRES_AGENTES = ["Yeimy Carrascal Torrado", "Andrés Ropero Vergel", "Natalia Ascanio Rincón", "Diego Pacheco Durán",
  "Paola Peñaranda Claro", "Wilmer Ovallos Picón", "Erika Manosalva Galvis", "Fabián Angarita Prada",
  "Marcela Trigos Amaya", "Sergio Coronel Melo", "Tatiana Barbosa Rizo", "Jhon Lobo Pallares"];
const INACTIVO_DESDE = fecha(2026, 5, 1, 13);
const agentes = NOMBRES_AGENTES.map((n, k) => {
  const ultimo = k === NOMBRES_AGENTES.length - 1;  // agente retirado: inactivo desde mayo de 2026
  const u = nuevoUsuario(n, DOM_GLOBAL, [ROL.AGENTE_SOPORTE], null, masD(fPersonal, k),
    ultimo ? { activo: false, fechaActualizacion: INACTIVO_DESDE } : {});
  return { doc: u, peso: [5, 4, 4, 3, 3, 3, 2, 2, 2, 2, 1.5, 1.5][k], inactivoDesde: ultimo ? INACTIVO_DESDE : null };
});
agentes.push({ doc: admin2, peso: 0.5, inactivoDesde: null });
const agenteActivo = a => f => !a.inactivoDesde || f < a.inactivoDesde;
const elegirAgente = f => elegirPeso(agentes.filter(a => agenteActivo(a)(f)).map(a => [a, a.peso])).doc;

// Usuarios de las empresas (alcance EMPRESA)
for (const e of empInfo) {
  if (e.situacion === "SIN" && e.i === 38) continue;          // Almacén Agrícola El Surco: sin usuarios
  const fAlta = e.inicioMembresia || masD(e.doc.fechaCreacion, 1);
  const conCliente = e.i % 2 === 0;                            // la mitad de los CLIENTE_ADMIN también tiene CLIENTE
  const adm = nuevoUsuario(persona(), e.dominio,
    conCliente ? [ROL.CLIENTE_ADMIN, ROL.CLIENTE] : [ROL.CLIENTE_ADMIN], e.doc._id, fAlta);
  e.usuarios.push({ doc: adm, alta: fAlta, baja: null, esCliente: conCliente });

  let cantidad;
  if (e.situacion === "SIN") cantidad = 0;
  else if (e.situacion === "P") cantidad = 2;
  else cantidad = { BASICO: entre(3, 6), ESTANDAR: entre(6, 12), PREMIUM: entre(12, 24) }[e.planClave];

  for (let k = 0; k < cantidad; k++) {
    const alta = menor(masD(fAlta, entre(0, 120)), masD(CORTE, -2));
    const u = nuevoUsuario(persona(), e.dominio, [ROL.CLIENTE], e.doc._id, alta);
    e.usuarios.push({ doc: u, alta, baja: null, esCliente: true });
  }
  // Un usuario desactivado en una de cada cuatro empresas con membresía (no cuenta para maxUsuarios).
  if (e.situacion !== "SIN" && e.situacion !== "P" && e.i % 4 === 1) {
    const alta = masD(fAlta, entre(0, 30));
    const baja = masD(alta, entre(90, 200));
    if (baja < CORTE) {
      const u = nuevoUsuario(persona(), e.dominio, [ROL.CLIENTE], e.doc._id, alta, { activo: false, fechaActualizacion: baja });
      e.usuarios.push({ doc: u, alta, baja, esCliente: true });
    }
  }
  e.clientes = e.usuarios.filter(x => x.esCliente);
}
const usuarioActivoEn = (x, f) => x.alta <= f && (!x.baja || f < x.baja);

// ---------------------------------------------------------------------
// 6. Membresías y pagos (con los periodos sin soporte)
// ---------------------------------------------------------------------
const membresias = [];
const pagos = [];
const MODULOS_POR_PLAN = { BASICO: [1, 2], ESTANDAR: [2, 3], PREMIUM: [4, 5] };
const METODOS = [["PSE", 40], ["TRANSFERENCIA", 25], ["TARJETA", 20], ["CONSIGNACION", 10], ["EFECTIVO", 5]];
const prefijoRef = { PSE: "PSE", TRANSFERENCIA: "TRF", TARJETA: "TDC", CONSIGNACION: "CNS", EFECTIVO: "EFE" };
let consecutivoPago = 100000;

const nuevoPago = (e, mem, f, resultado) => {
  const metodo = elegirPeso(METODOS);
  pagos.push(Object.assign({ _id: oid(f, "06") }, comunes(f), {
    membresiaId: mem._id, empresaId: e.doc._id,
    monto: NumberDecimal(PLANES[e.planClave].precio + ".00"),
    fechaPago: f, metodoPago: metodo,
    referencia: `${prefijoRef[metodo]}-${f.getUTCFullYear()}${pad(f.getUTCMonth() + 1, 2)}-${++consecutivoPago}`,
    resultado, registradoPorId: prob(0.8) ? admin1._id : admin2._id
  }));
};

for (const e of empInfo) {
  if (e.situacion === "SIN") continue;
  const plan = PLANES[e.planClave];
  const [minM, maxM] = MODULOS_POR_PLAN[e.planClave];
  const n = Math.min(entre(minM, maxM), plan.limites.maxModulos);
  // Ventas (online o en sitio) casi siempre está contratado.
  const claves = MODULOS.map(m => [m, PESO_MODULO[m] * rnd()]).sort((a, b) => b[1] - a[1]);
  const modulos = MODULOS.filter(m => claves.slice(0, n).some(c => c[0] === m));

  const inicio = e.inicioMembresia;
  let vencimiento, estadoMem, finSoporte = null, suspension = null;
  if (e.situacion === "V") { vencimiento = fecha(2026, 9, 30, 23, 59); estadoMem = "ACTIVA"; finSoporte = vencimiento; }
  else if (e.situacion === "P") { vencimiento = masD(inicio, 365); estadoMem = "PENDIENTE"; }
  else { vencimiento = fecha(2027, inicio.getUTCMonth() + 1, Math.min(inicio.getUTCDate(), 28), 23, 59); estadoMem = "ACTIVA"; }
  if (e.situacion.startsWith("S")) suspension = Number(e.situacion.slice(1));

  const mem = Object.assign({ _id: oid(inicio, "05") }, comunes(inicio), {
    empresaId: e.doc._id, planId: plan.doc._id, modulosContratados: modulos,
    fechaInicio: inicio, fechaVencimiento: vencimiento, estado: estadoMem
  });
  membresias.push(mem);
  e.mem = mem;
  e.modulos = modulos;
  e.huecos = [];

  if (e.situacion === "P") { e.finSoporte = inicio; continue; }   // sin pagos: nunca tuvo soporte

  // Un pago por mes desde el mes de inicio (o noviembre de 2025) hasta octubre de 2026.
  let ultimaFecha = inicio;
  for (const [y, m] of MESES) {
    const diaPago = fecha(y, m, entre(1, 8), entre(13, 21), entre(0, 59));
    if (diaPago < inicio) continue;
    if (finSoporte && diaPago > finSoporte) break;
    if (suspension && y === 2026 && m >= suspension) {
      // Pago fallido: la transacción de HU17 deja la membresía SUSPENDIDA.
      nuevoPago(e, mem, diaPago, "FALLIDO");
      if (m === suspension) {
        mem.estado = "SUSPENDIDA";
        mem.fechaActualizacion = diaPago;
        finSoporte = diaPago;
      }
      ultimaFecha = diaPago;
      continue;
    }
    if (prob(0.07)) {
      // Fallo puntual y reintento aprobado unos días después: sin soporte entre los dos.
      nuevoPago(e, mem, diaPago, "FALLIDO");
      const reintento = masH(diaPago, entre(30, 140));
      nuevoPago(e, mem, reintento, "APROBADO");
      e.huecos.push([diaPago, reintento]);
      mem.fechaActualizacion = reintento;
      ultimaFecha = reintento;
    } else {
      nuevoPago(e, mem, diaPago, "APROBADO");
      ultimaFecha = diaPago;
    }
  }
  if (mem.estado === "ACTIVA" && mem.fechaActualizacion < ultimaFecha) mem.fechaActualizacion = ultimaFecha;
  e.finSoporte = finSoporte;
}

const tieneSoporte = (e, f) => {
  if (!e.mem || e.mem.estado === "PENDIENTE") return false;
  if (f < e.mem.fechaInicio) return false;
  if (e.finSoporte && f >= e.finSoporte) return false;
  return !e.huecos.some(([a, b]) => f >= a && f < b);
};
const conMembresia = empInfo.filter(e => e.mem && e.mem.estado !== "PENDIENTE");

// ---------------------------------------------------------------------
// 7. Solicitudes, capacitaciones e inscripciones
// ---------------------------------------------------------------------
const solicitudes = [];
const capacitaciones = [];
const inscripciones = [];
const asistenciasPorUsuario = new Map();   // usuarioId -> [{ modulo, fechaFin }] (para A9)

const TEMAS = {
  VENTAS_ONLINE: ["Configuración de la tienda web y pasarelas de pago", "Gestión de pedidos web y devoluciones", "Sincronización de catálogo y precios con la tienda"],
  INVENTARIOS: ["Traslados entre bodegas y ajustes de inventario", "Alertas de stock y conteo físico", "Códigos de barras y kardex"],
  COMPRAS: ["Órdenes de compra y aprobaciones", "Recepción de mercancía y cuentas por pagar", "Pagos a proveedores y retenciones"],
  PRODUCCION: ["Recetas de materia prima y costos de fabricación", "Control de lotes y trazabilidad", "Fases del proceso y sensores de planta"],
  VENTAS_EN_SITIO: ["Facturación rápida en caja", "Cierre de caja y manejo de turnos", "Datáfonos, impresoras y cajón monedero"]
};
const instructorEn = f => elegirAgente(f);

const nuevaCapacitacion = (tipo, modulo, inicio, empresaId, solicitudId, cupo, titulo, estadoCap = "PROGRAMADA", fCreaDada = null) => {
  const duracion = elegir([60, 90, 120, 180]);
  const fCrea = fCreaDada || menor(masD(inicio, -entre(15, 40)), masD(CORTE, -1));
  const c = Object.assign({ _id: oid(fCrea, "0a") }, comunes(fCrea), {
    titulo, tipo, modulo, empresaId, fechaInicio: inicio, duracionMinutos: duracion,
    fechaFin: new Date(inicio.getTime() + duracion * 60000), cupo, cuposOcupados: 0,
    instructorId: instructorEn(menor(inicio, CORTE))._id, estado: estadoCap, solicitudId
  });
  capacitaciones.push(c);
  return c;
};

const yaInscritoEnModulo = new Set();   // usuarioId|modulo
const inscribir = (c, candidatos, cuantos) => {
  const elegidos = [];
  for (const x of mezclar(candidatos)) {
    if (elegidos.length >= cuantos) break;
    const clave = x.doc._id.toString() + "|" + c.modulo;
    if (yaInscritoEnModulo.has(clave)) continue;
    let fIns = masD(c.fechaInicio, -entre(1, 15));
    if (fIns > CORTE) fIns = masH(CORTE, -entre(1, 400));
    const emp = empInfo.find(e => e.doc._id === x.doc.empresaId);
    if (!usuarioActivoEn(x, fIns) || !tieneSoporte(emp, fIns)) continue;
    if (x.baja && x.baja < c.fechaFin) continue;
    yaInscritoEnModulo.add(clave);
    elegidos.push([x, fIns]);
  }
  for (const [x, fIns] of elegidos) {
    const ins = Object.assign({ _id: oid(fIns, "0c") }, comunes(fIns), {
      capacitacionId: c._id, usuarioId: x.doc._id, empresaId: x.doc.empresaId,
      asistio: null, marcadoPorId: null, fechaMarcado: null
    });
    if (c.fechaInicio < CORTE) {
      const r = rnd();
      if (r < 0.96) {
        ins.asistio = r < 0.82;
        ins.marcadoPorId = prob(0.9) ? c.instructorId : admin1._id;
        ins.fechaMarcado = new Date(c.fechaInicio.getTime() + entre(5, c.duracionMinutos) * 60000);
        ins.fechaActualizacion = ins.fechaMarcado;
        if (ins.asistio) {
          const k = ins.usuarioId.toString();
          if (!asistenciasPorUsuario.has(k)) asistenciasPorUsuario.set(k, []);
          asistenciasPorUsuario.get(k).push({ modulo: c.modulo, fechaFin: c.fechaFin });
        }
      }
    }
    inscripciones.push(ins);
  }
  c.cuposOcupados = elegidos.length;
};

// Masivas: 2 por mes de diciembre de 2025 a diciembre de 2026 (las de noviembre
// y diciembre de 2026 quedan programadas a futuro).
const MESES_CAP = MESES.slice(1).concat([[2026, 11], [2026, 12]]);
let turnoModulo = 0;
const ORDEN_CAP = ["VENTAS_EN_SITIO", "INVENTARIOS", "VENTAS_ONLINE", "COMPRAS", "PRODUCCION"];
MESES_CAP.forEach(([y, m], k) => {
  for (const dia of [entre(5, 12), entre(17, 25)]) {
    const modulo = ORDEN_CAP[turnoModulo++ % ORDEN_CAP.length];
    const inicio = fecha(y, m, dia, elegir([14, 19]));
    const cancelada = (y === 2026 && m === 4 && dia < 15);
    const c = nuevaCapacitacion("MASIVA", modulo, inicio, null, null, elegir([30, 40, 50, 60]),
      elegir(TEMAS[modulo]), cancelada ? "CANCELADA" : "PROGRAMADA");
    if (cancelada) { c.fechaActualizacion = masD(inicio, -3); continue; }
    const candidatos = conMembresia.filter(e => e.modulos.includes(modulo)).flatMap(e => e.clientes);
    inscribir(c, candidatos, Math.floor(c.cupo * (0.55 + rnd() * 0.4)));
  }
});

// Personalizadas: con solicitud (PROGRAMADA), sin solicitud previa, y solicitudes
// pendientes o rechazadas. Solo planes con capacitaciones incluidas.
const nuevaSolicitud = (e, modulo, fCrea, estadoSol, extra = {}) => {
  const adm = e.usuarios[0].doc;
  const s = Object.assign({ _id: oid(fCrea, "0b") }, comunes(fCrea), {
    empresaId: e.doc._id, solicitanteId: adm._id, modulo,
    descripcion: extra.descripcion || `El equipo necesita reforzar ${TEMAS[modulo][0].toLowerCase()}. Varios usuarios nuevos cometen los mismos errores al registrar operaciones.`,
    participantesEstimados: entre(4, 15),
    estado: estadoSol, capacitacionId: null, motivoRechazo: null, atendidaPorId: null
  });
  if (extra.fechaPreferida) s.fechaPreferida = extra.fechaPreferida;
  solicitudes.push(s);
  return s;
};
const conCupoPlan = conMembresia.filter(e => PLANES[e.planClave].limites.capacitacionesIncluidas > 0 && !e.finSoporte);
const usadasPorEmpresa = new Map();
const puedeSolicitar = e => (usadasPorEmpresa.get(e.i) || 0) < PLANES[e.planClave].limites.capacitacionesIncluidas;
const contarSolicitud = e => usadasPorEmpresa.set(e.i, (usadasPorEmpresa.get(e.i) || 0) + 1);

// 8 solicitudes programadas: 6 ya realizadas y 2 a futuro.
const fechasProgramadas = [fecha(2026, 2, 18, 14), fecha(2026, 3, 11, 19), fecha(2026, 4, 22, 14), fecha(2026, 5, 13, 14),
  fecha(2026, 6, 10, 19), fecha(2026, 7, 8, 14), fecha(2026, 11, 18, 14), fecha(2026, 12, 2, 19)];
let idxEmp = 0;
for (const inicio of fechasProgramadas) {
  let e;
  do { e = conCupoPlan[idxEmp++ % conCupoPlan.length]; } while (!puedeSolicitar(e) || !tieneSoporte(e, masD(inicio, -30)));
  const modulo = elegir(e.modulos);
  const fSol = menor(masD(inicio, -entre(20, 35)), masD(CORTE, -entre(4, 10)));
  const s = nuevaSolicitud(e, modulo, fSol, "PROGRAMADA", { fechaPreferida: masD(inicio, entre(-3, 3)) });
  contarSolicitud(e);
  const agente = elegirAgente(fSol);
  // El agente programa la capacitación unos días después de recibir la solicitud.
  const fProg = menor(masD(fSol, entre(1, 5)), masD(CORTE, -1));
  const c = nuevaCapacitacion("PERSONALIZADA", modulo, inicio, e.doc._id, s._id, entre(10, 20),
    `${elegir(TEMAS[modulo])} · ${e.doc.razonSocial.replace(/ S\.A\.S\.$/, "")}`, "PROGRAMADA", fProg);
  s.capacitacionId = c._id;
  s.atendidaPorId = agente._id;
  s.fechaActualizacion = fProg;
  inscribir(c, e.clientes, Math.min(c.cupo, e.clientes.length));
}
// 2 personalizadas creadas por un agente sin solicitud previa (decisión de 1.3).
for (const inicio of [fecha(2026, 3, 25, 14), fecha(2026, 8, 19, 19)]) {
  const e = conCupoPlan[idxEmp++ % conCupoPlan.length];
  const modulo = elegir(e.modulos);
  const c = nuevaCapacitacion("PERSONALIZADA", modulo, inicio, e.doc._id, null, entre(8, 15),
    `${elegir(TEMAS[modulo])} · ${e.doc.razonSocial.replace(/ S\.A\.S\.$/, "")}`);
  inscribir(c, e.clientes, Math.min(c.cupo, e.clientes.length));
}
// 2 rechazadas.
const MOTIVOS = [
  "La fecha preferida coincide con el cierre contable de la empresa. Se propuso otra fecha y no hubo respuesta en 15 días.",
  "El tema pedido ya está cubierto por la capacitación masiva del mismo mes. Se inscribió al equipo en esa sesión."
];
for (const motivo of MOTIVOS) {
  let e, fSol;
  do {
    e = conCupoPlan[idxEmp++ % conCupoPlan.length];
    fSol = fecha(2026, entre(5, 8), entre(1, 25), 15);
  } while (!tieneSoporte(e, fSol));
  const s = nuevaSolicitud(e, elegir(e.modulos), fSol, "RECHAZADA");
  s.motivoRechazo = motivo;
  s.atendidaPorId = elegirAgente(fSol)._id;
  s.fechaActualizacion = masD(fSol, entre(2, 6));
}
// 4 pendientes en la bandeja del agente (octubre de 2026). Una volvió a PENDIENTE
// porque se eliminó su capacitación (HU36 v.4) y conserva atendidaPorId.
for (let k = 0; k < 4; k++) {
  let e, intentos = 0;
  do { e = conCupoPlan[idxEmp++ % conCupoPlan.length]; } while ((!puedeSolicitar(e) || !tieneSoporte(e, CORTE)) && ++intentos < 100);
  const fSol = fecha(2026, 10, entre(5, 30), entre(13, 21));
  const s = nuevaSolicitud(e, elegir(e.modulos), fSol, "PENDIENTE", { fechaPreferida: fecha(2026, entre(11, 12), entre(1, 28), 14) });
  contarSolicitud(e);
  if (k === 0) { s.atendidaPorId = elegirAgente(fSol)._id; s.fechaActualizacion = masD(fSol, 1); }
}

// ---------------------------------------------------------------------
// 8. Tickets y comentarios
// ---------------------------------------------------------------------
const PLANTILLAS = {
  VENTAS_ONLINE: {
    incidentes: [
      ["La pasarela rechaza pagos con tarjeta débito", "Desde ayer los clientes de la tienda web ven 'transacción rechazada' al pagar con tarjeta débito. Con crédito sí pasa."],
      ["Pedidos web no se sincronizan con el inventario", "Los pedidos de la tienda llegan al ERP con varias horas de retraso y vendimos productos que ya estaban agotados."],
      ["Error 502 al finalizar la compra", "El checkout muestra error 502 cuando el cliente confirma el pago. El cobro queda hecho pero el pedido no se crea."],
      ["Pedido duplicado después de pagar con PSE", "Un cliente pagó una vez por PSE y en el ERP aparecen dos pedidos con la misma referencia."],
      ["El carrito se vacía al iniciar sesión", "Los clientes agregan productos, inician sesión para pagar y el carrito queda vacío."],
      ["Precios de la tienda distintos a los del ERP", "Cambiamos la lista de precios en el ERP y la tienda web sigue mostrando los valores anteriores."],
      ["No llega la confirmación de pago al ERP", "La pasarela marca el pago como aprobado pero el pedido sigue en 'pendiente de pago' en el ERP."]
    ],
    sugerencias: [
      ["Permitir pagos con Nequi en la tienda", "Muchos clientes preguntan si pueden pagar con Nequi. Sería útil tenerlo como medio de pago."],
      ["Aviso al cliente cuando el pedido se despacha", "Proponemos que la tienda envíe un correo automático cuando el pedido sale de bodega."],
      ["Reporte de carritos abandonados", "Queremos un listado de carritos abandonados para contactar a esos clientes."]
    ]
  },
  VENTAS_EN_SITIO: {
    incidentes: [
      ["La impresora de la caja no imprime facturas", "Después de la actualización la impresora térmica no responde. La factura queda registrada pero no sale el tiquete."],
      ["Diferencia en el cierre de caja del turno", "El cierre del turno de la mañana muestra un faltante que no coincide con el efectivo contado."],
      ["El datáfono no responde al cobrar", "Al seleccionar pago con tarjeta la caja se queda esperando el datáfono y luego cancela la venta."],
      ["El lector de código de barras escribe caracteres raros", "Al escanear productos aparecen símbolos en lugar del código y la caja no encuentra el producto."],
      ["Caja bloqueada por un turno abierto de otro cajero", "Un cajero no cerró su turno y ahora nadie puede abrir la caja para facturar."],
      ["Factura electrónica POS rechazada", "Varias facturas del día quedaron en estado rechazado y el cliente no recibió el documento."],
      ["El cajón monedero no abre", "Al registrar un pago en efectivo el cajón no se abre y toca abrirlo con la llave."]
    ],
    sugerencias: [
      ["Atajo de teclado para repetir la última venta", "En hora pico sería más rápido repetir la última venta con una tecla."],
      ["Mostrar el vuelto en letra grande", "Los cajeros piden que el valor del vuelto se vea más grande en la pantalla."],
      ["Cierre de caja con conteo por denominación", "Proponemos que el cierre pida el conteo de billetes y monedas por denominación."]
    ]
  },
  INVENTARIOS: {
    incidentes: [
      ["Existencias negativas después de un traslado", "Hicimos un traslado de la bodega principal a la del punto de venta y el producto quedó con existencia negativa."],
      ["No llegan las alertas de stock mínimo", "Configuramos el stock mínimo pero no llega ningún correo cuando el producto baja del límite."],
      ["El ajuste por conteo físico no se aplica", "Registramos el ajuste del conteo físico y el saldo de la bodega sigue igual."],
      ["Código de barras repetido en dos productos", "Al escanear un código aparecen dos productos distintos y la caja toma el equivocado."],
      ["El kardex no coincide con el saldo de la bodega", "El saldo del kardex de un producto no coincide con la existencia que muestra la bodega."],
      ["La entrada de mercancía no suma existencias", "Registramos la entrada de un pedido del proveedor y las existencias no aumentaron."]
    ],
    sugerencias: [
      ["Importar el conteo físico desde Excel", "Sería útil cargar el conteo físico desde una hoja de cálculo en lugar de digitarlo."],
      ["Alerta de productos próximos a vencer", "Proponemos una alerta para productos con fecha de vencimiento cercana."],
      ["Ver existencias de todas las bodegas en una pantalla", "Queremos consultar en una sola vista la existencia de un producto en todas las bodegas."]
    ]
  },
  COMPRAS: {
    incidentes: [
      ["No se puede aprobar la orden de compra", "La orden de compra queda en 'pendiente de aprobación' y el botón de aprobar no responde."],
      ["Cuenta por pagar duplicada", "Al registrar la factura del proveedor se crearon dos cuentas por pagar por el mismo valor."],
      ["El proveedor no aparece al crear la orden", "El proveedor está creado pero no sale en la lista al hacer una orden de compra nueva."],
      ["Retención mal calculada en la factura del proveedor", "La retención en la fuente de la factura no corresponde a la tarifa configurada."],
      ["La recepción parcial cierra la orden completa", "Recibimos la mitad del pedido y el sistema marcó la orden como recibida en su totalidad."]
    ],
    sugerencias: [
      ["Comparar cotizaciones de varios proveedores", "Proponemos poder registrar varias cotizaciones y elegir la mejor desde la orden de compra."],
      ["Recordatorio de cuentas por pagar próximas a vencer", "Sería útil un aviso semanal con las cuentas por pagar que vencen."],
      ["Adjuntar la factura del proveedor en PDF", "Queremos guardar el PDF de la factura junto con la cuenta por pagar."]
    ]
  },
  PRODUCCION: {
    incidentes: [
      ["El costo del lote no incluye la mano de obra", "Al cerrar el lote el costo de fabricación solo suma la materia prima."],
      ["La receta descuenta de la bodega equivocada", "La orden de producción consumió materia prima de la bodega de producto terminado."],
      ["Temperatura del sensor fuera de rango", "El sensor de la línea reporta una temperatura que no corresponde a la medida del termómetro de planta."],
      ["La orden queda detenida en la fase de empaque", "La orden de producción no avanza de empaque a almacenamiento aunque el lote ya está listo."],
      ["El sensor dejó de enviar datos", "Desde el cambio de turno el ERP no recibe lecturas del sensor de la máquina."],
      ["No se puede cerrar el lote con unidades defectuosas", "Control de calidad rechazó unidades del lote y el sistema no deja cerrarlo."]
    ],
    sugerencias: [
      ["Tablero de producción por turno", "Proponemos un tablero con las unidades producidas por turno y por máquina."],
      ["Alerta cuando el sensor pasa un umbral", "Queremos que el sistema avise si la temperatura del sensor supera un valor configurable."],
      ["Copiar una receta para un producto nuevo", "Sería útil duplicar una receta existente para crear la de un producto parecido."]
    ]
  }
};

const LOGS_API = [
  'POST /api/v2/pagos/confirmar -> 502 Bad Gateway {"codigo":"GW_TIMEOUT","tiempoMs":30012}',
  'POST /api/v2/pedidos -> 409 Conflict {"codigo":"PEDIDO_DUPLICADO","referencia":"%REF%"}',
  'GET /api/v2/productos/sync -> 429 Too Many Requests {"reintentarEn":60}',
  'POST /api/v2/webhooks/pasarela -> 401 Unauthorized {"codigo":"FIRMA_INVALIDA"}',
  'POST /api/v2/pagos -> 400 Bad Request {"codigo":"TARJETA_NO_SOPORTADA","franquicia":"DEBITO"}'
];
const NAVEGADORES = ["Chrome 141 en Windows 11", "Edge 141 en Windows 10", "Firefox 144 en Windows 11", "Safari 26 en iPhone", "Chrome 141 en Android 15"];

const detalle = (modulo, e, f) => {
  const ym = `${f.getUTCFullYear()}${pad(f.getUTCMonth() + 1, 2)}`;
  switch (modulo) {
    case "VENTAS_ONLINE": {
      const d = {
        pasarelaPagoId: `${elegir(["WOMPI", "PAYU", "EPAYCO", "MERCADOPAGO"])}-${entre(100000, 999999)}`,
        urlError: `https://tienda.${e.dominio}/checkout/${elegir(["pago", "confirmacion", "carrito", "envio"])}?orden=${entre(10000, 99999)}`,
        navegador: elegir(NAVEGADORES)
      };
      if (prob(0.5)) d.logApi = elegir(LOGS_API).replace("%REF%", `WEB-${entre(10000, 99999)}`);
      return d;
    }
    case "PRODUCCION": {
      const d = {
        maquinariaId: `${elegir(["MEZ", "EMP", "HOR", "MOL", "ENV"])}-${pad(entre(1, 8), 2)}`,
        codigoLote: `L${ym}-${pad(entre(1, 250), 3)}`,
        faseProceso: elegir(["PREPARACION", "MEZCLADO", "TRANSFORMACION", "CONTROL_CALIDAD", "EMPAQUE", "ALMACENAMIENTO"])
      };
      if (prob(0.7)) d.temperaturaSensor = Double(entre(150, 950) / 10);
      return d;
    }
    case "INVENTARIOS": {
      const d = {
        codigoBodega: elegir(["BOD-PRINCIPAL", "BOD-02", "BOD-03", "BOD-PUNTO-VENTA", "BOD-TRANSITO"]),
        codigoProducto: `SKU-${pad(entre(1, 99999), 5)}`,
        tipoOperacion: elegir(["ENTRADA", "SALIDA", "TRASLADO", "AJUSTE", "ALERTA_STOCK"])
      };
      if (prob(0.6)) d.codigoBarras = `770${pad(entre(0, 9999999999), 10)}`;
      if (prob(0.5)) d.existenciaSistema = entre(-40, 500);
      return d;
    }
    case "COMPRAS": {
      const d = {
        nitProveedor: nitConDv(entre(800000000, 901999999)),
        etapa: elegir(["ORDEN_COMPRA", "RECEPCION", "CUENTA_POR_PAGAR", "PAGO_PROVEEDOR"])
      };
      if (prob(0.7)) d.numeroOrdenCompra = `OC-${f.getUTCFullYear()}-${pad(entre(1, 9999), 5)}`;
      if (prob(0.5)) d.numeroFacturaProveedor = `FE-${entre(1000, 99999)}`;
      if (prob(0.5)) d.montoInvolucrado = NumberDecimal(entre(200, 9000) * 1000 + ".00");
      return d;
    }
    case "VENTAS_EN_SITIO": {
      const d = {
        cajaId: `CAJA-${pad(entre(1, 6), 2)}`,
        turnoId: `T-${ym}${pad(f.getUTCDate(), 2)}-${elegir(["M", "T", "N"])}`,
        dispositivo: elegir(["IMPRESORA", "LECTOR_CODIGO", "CAJON_MONEDERO", "DATAFONO", "NINGUNO"])
      };
      if (prob(0.6)) d.numeroFactura = `POS-${pad(entre(1, 99999), 6)}`;
      if (prob(0.6)) d.medioPago = elegir(["EFECTIVO", "TARJETA", "TRANSFERENCIA", "MIXTO"]);
      return d;
    }
  }
};

// Ritmo mensual de tickets (diciembre sube por la temporada de ventas).
const PESO_MES = [7.5, 11, 8.5, 8, 8, 8, 8.5, 8, 8.5, 8, 8, 8];
// Peso de cada empresa: más usuarios, más tickets.
const pesoEmpresa = conMembresia.map(e => [e, e.clientes.length + e.usuarios.length * 0.3]);

// A9: el usuario que asistió a la capacitación de un módulo reporta menos
// incidentes de ese módulo en los 90 días siguientes, y más en los 90 previos.
const EFECTO_DESPUES = { VENTAS_ONLINE: 0.7, VENTAS_EN_SITIO: 0.62, INVENTARIOS: 0.75, COMPRAS: 1.0, PRODUCCION: 0.8 };
const factorA9 = (usuarioId, modulo, f) => {
  const lista = asistenciasPorUsuario.get(usuarioId.toString());
  if (!lista) return 1;
  let factor = 1;
  for (const a of lista) {
    if (a.modulo !== modulo) continue;
    const dt = (f - a.fechaFin) / D;
    if (dt > -90 && dt <= 0) factor *= 1.25;
    else if (dt > 0 && dt <= 90) factor *= EFECTO_DESPUES[modulo];
  }
  return factor;
};

const COMENTARIOS_CLIENTE = [
  "Adjunto la captura del error. Sigue pasando esta mañana.",
  "Ya reiniciamos el equipo y el problema continúa.",
  "¿Tienen una fecha estimada para la solución?",
  "Confirmo que después del cambio ya funciona.",
  "Volvió a pasar hoy con otro cliente.",
  "Le envié por correo el número de la factura afectada."
];
const COMENTARIOS_AGENTE = [
  "Estamos revisando el log del servidor. Le aviso cuando tenga el diagnóstico.",
  "Por favor confirme si el error aparece también en otro equipo.",
  "Aplicamos la corrección en su ambiente. ¿Puede probar de nuevo?",
  "El caso está documentado en la base de conocimientos y le dejé el artículo vinculado.",
  "Necesitamos acceso remoto al equipo de caja para revisar el controlador.",
  "Escalamos el caso al equipo de desarrollo con la evidencia que envió."
];

const tickets = [];
const comentarios = [];
const TOTAL_TICKETS = 8000;
let intentos = 0;

while (tickets.length < TOTAL_TICKETS) {
  if (++intentos > TOTAL_TICKETS * 50) throw new Error("No se pudieron generar los tickets: revisa las fechas de soporte.");
  const [y, m] = MESES[elegirPeso(MESES.map((_, k) => [k, PESO_MES[k]]))];
  const diasMes = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const creacion = fecha(y, m, entre(1, diasMes), entre(12, 23), entre(0, 59));
  if (creacion > masH(CORTE, -1)) continue;
  const e = elegirPeso(pesoEmpresa);
  if (!tieneSoporte(e, creacion)) continue;

  const tipo = prob(0.14) ? "SUGERENCIA" : "INCIDENTE";
  const modulo = elegirPeso(e.modulos.map(md => [md, PESO_MODULO[md]]));
  const activos = e.usuarios.filter(x => usuarioActivoEn(x, creacion));
  if (!activos.length) continue;
  const solicitante = elegirPeso(activos.map(x => [x, tipo === "INCIDENTE" ? factorA9(x.doc._id, modulo, creacion) : 1])).doc;
  const creador = prob(0.82) ? solicitante : elegirAgente(creacion);
  const plan = PLANES[e.planClave];
  const limite = menor(CORTE, e.finSoporte || CORTE);   // sin soporte, nadie atiende el ticket

  // Línea de tiempo del ticket
  const horasResp = plan.limites.horasRespuesta;
  const tAsig = masH(creacion, prob(0.86) ? horasResp * rnd() * 0.9 : horasResp * (1 + rnd() * 1.5));
  // Uno de cada doce casos es complejo y tarda varias veces más (deja tickets abiertos al corte).
  const complejo = prob(0.085) ? entre(6, 16) : 1;
  const horasBase = HORAS_RESOLUCION[modulo] * FACTOR_PLAN[e.planClave] * (tipo === "SUGERENCIA" ? 4 : 1) * complejo;
  const tRes = masH(tAsig, horasBase * (0.3 + rnd() * 1.4));
  const tCierre = masH(tRes, entre(12, 96));
  const reabre = tipo === "INCIDENTE" && prob(0.04);
  const tReabre = masH(tRes, entre(6, 48));
  const tRes2 = masH(tReabre, horasBase * (0.2 + rnd() * 0.6));
  const tCierre2 = masH(tRes2, entre(12, 72));
  const directoACerrado = prob(0.03);          // duplicado o sin respuesta del cliente
  const atascado = prob(0.005);                 // quedó sin asignar

  let agente = elegirAgente(tAsig < CORTE ? tAsig : creacion);
  const pasos = [["ABIERTO", creacion, creador._id]];
  if (!atascado && tAsig <= limite) {
    pasos.push(["EN_PROCESO", tAsig, agente._id]);
    if (directoACerrado) {
      if (tRes <= limite) pasos.push(["CERRADO", tRes, agente._id]);
    } else if (tRes <= limite) {
      pasos.push(["RESUELTO", tRes, agente._id]);
      if (reabre && tReabre <= limite) {
        pasos.push(["EN_PROCESO", tReabre, solicitante._id]);
        if (tRes2 <= limite) {
          pasos.push(["RESUELTO", tRes2, agente._id]);
          if (tCierre2 <= limite) pasos.push(["CERRADO", tCierre2, prob(0.5) ? solicitante._id : agente._id]);
        }
      } else if (!reabre && tCierre <= limite) {
        pasos.push(["CERRADO", tCierre, prob(0.5) ? solicitante._id : agente._id]);
      }
    }
  }
  // El agente retirado no puede quedar con trabajo después de su retiro.
  const ultimo = pasos[pasos.length - 1];
  const agRetirado = agentes.find(a => a.doc === agente).inactivoDesde;
  if (agRetirado && pasos.length > 1 && (ultimo[1] >= agRetirado || !["CERRADO", "RESUELTO"].includes(ultimo[0]))) {
    agente = elegirPeso(agentes.filter(a => !a.inactivoDesde).map(a => [a, a.peso])).doc;
    for (const p of pasos) if (p[0] !== "ABIERTO" && p[2] !== solicitante._id) p[2] = agente._id;
  }

  const estadoFinal = ultimo[0];
  let fechaResolucion = null;
  for (const [est, f] of pasos) {
    if (est === "RESUELTO") fechaResolucion = f;
    if (est === "EN_PROCESO") fechaResolucion = null;
    if (est === "CERRADO" && !fechaResolucion) fechaResolucion = f;
  }
  const asignado = pasos.length > 1;
  const [titulo, descripcion] = elegir(tipo === "INCIDENTE" ? PLANTILLAS[modulo].incidentes : PLANTILLAS[modulo].sugerencias);

  const t = Object.assign({ _id: oid(creacion, "07") }, comunes(creacion), {
    empresaId: e.doc._id,
    creadorId: creador._id,
    solicitanteId: solicitante._id,
    agenteId: asignado ? agente._id : null,
    modulo, tipo, titulo, descripcion
  });
  if (tipo === "INCIDENTE") t.detalle = detalle(modulo, e, creacion);
  Object.assign(t, {
    estado: estadoFinal,
    historialEstados: pasos.map(([est, f, u]) => ({ estado: est, fecha: f, usuarioId: u })),
    fechaResolucion,
    fechaLimiteRespuesta: masH(creacion, horasResp),
    fechaPrimeraAsignacion: asignado ? tAsig : null
  });
  t.fechaActualizacion = ultimo[1];
  tickets.push(t);

  // Comentarios: uno de cada cuatro tickets asignados tiene conversación.
  if (asignado && prob(0.25)) {
    // Entre la asignación y el cierre (o el corte, si sigue abierto).
    const desde = tAsig.getTime();
    const cerrado = estadoFinal === "RESUELTO" || estadoFinal === "CERRADO";
    const fin = Math.min(cerrado ? ultimo[1].getTime() : limite.getTime(), limite.getTime());
    const n = entre(1, 4);
    const marcas = Array.from({ length: n }, () => desde + rnd() * (fin - desde)).sort((a, b) => a - b);
    marcas.forEach((ms, k) => {
      const fC = new Date(Math.floor(ms / 60000) * 60000 + 60000);
      if (fC > limite || fC <= tAsig || (cerrado && fC > ultimo[1])) return;
      const delAgente = k % 2 === 1 || creador._id === agente._id;
      const c = Object.assign({ _id: oid(fC, "08") }, comunes(fC), {
        ticketId: t._id,
        autorId: delAgente ? agente._id : solicitante._id,
        texto: elegir(delAgente ? COMENTARIOS_AGENTE : COMENTARIOS_CLIENTE),
        editado: false
      });
      if (prob(0.05)) {
        const fEd = new Date(fC.getTime() + entre(2, 30) * 60000);
        if (fEd <= limite) { c.editado = true; c.fechaActualizacion = fEd; }
      }
      comentarios.push(c);
    });
  }
}

// ---------------------------------------------------------------------
// 9. Artículos de la base de conocimientos
// ---------------------------------------------------------------------
// [título, etiquetas, síntoma, causa, [pasos], visibilidad]
const ARTICULOS = {
  VENTAS_ONLINE: [
    ["Error de sincronización entre la tienda web y el inventario", ["sincronizacion", "tienda-web", "inventario", "existencias"],
      "Los productos agotados siguen disponibles en la tienda web o las existencias tardan horas en actualizarse.",
      "La tarea de sincronización se detiene cuando la API de la tienda responde 429 o cuando venció el token de integración.",
      ["Revisar en Configuración > Integraciones la fecha del último envío exitoso.", "Renovar el token de la tienda si aparece vencido.", "Ejecutar Sincronizar ahora y confirmar que el contador de pendientes llega a cero."], "PUBLICO"],
    ["La pasarela de pago rechaza transacciones con tarjeta débito", ["pasarela", "pagos", "tarjeta", "rechazo"],
      "El cliente ve 'transacción rechazada' al pagar con débito y con crédito el pago pasa.",
      "La franquicia débito no está habilitada en la cuenta de la pasarela o el comercio no activó la autenticación 3DS.",
      ["Entrar al panel de la pasarela y revisar las franquicias habilitadas.", "Activar débito y 3DS si están apagados.", "Hacer una compra de prueba con valor bajo."], "PUBLICO"],
    ["Pedidos web duplicados después de un pago con PSE", ["pse", "pedidos", "duplicados", "pasarela"],
      "Un pago por PSE genera dos pedidos con la misma referencia.",
      "El cliente vuelve a la tienda antes de que llegue la confirmación y el navegador reenvía el formulario.",
      ["Anular el pedido sin pago asociado.", "Verificar que el webhook de la pasarela esté activo.", "Activar la opción de referencia única por pedido."], "PUBLICO"],
    ["El carrito pierde los productos al iniciar sesión", ["carrito", "sesion", "navegador", "cookies"],
      "El cliente agrega productos sin sesión y al iniciar sesión el carrito queda vacío.",
      "El navegador bloquea las cookies de terceros y el carrito anónimo no se une con el del usuario.",
      ["Pedir al cliente que permita cookies del dominio de la tienda.", "Activar en la tienda la opción de unir carritos al iniciar sesión."], "PUBLICO"],
    ["Cómo leer el log de la API de pagos", ["log-api", "pasarela", "diagnostico", "codigos-http"],
      "El ticket trae un fragmento del log de la API y hay que saber qué parte del flujo falló.",
      "Cada línea del log tiene el método, la ruta, el código HTTP y el cuerpo con el código interno del error.",
      ["Ubicar el código HTTP: 4xx es un dato mal enviado, 5xx es falla del servicio.", "Leer el campo codigo del cuerpo y buscarlo en la tabla de errores de la pasarela.", "Si es GW_TIMEOUT, revisar la latencia del día en el monitor."], "INTERNO"],
    ["Webhook de confirmación de pago no llega al ERP", ["webhook", "pasarela", "confirmacion", "firma"],
      "La pasarela aprueba el pago y el pedido sigue pendiente de pago en el ERP.",
      "La firma del webhook no coincide porque se cambió la llave secreta en la pasarela y no en el ERP.",
      ["Comparar la llave secreta de la pasarela con la configurada en el ERP.", "Reenviar el evento desde el panel de la pasarela.", "Confirmar en el log una respuesta 200."], "INTERNO"],
    ["Precios de la tienda web distintos a la lista de precios del ERP", ["precios", "sincronizacion", "tienda-web", "lista-precios"],
      "La tienda muestra precios anteriores después de cambiar la lista en el ERP.",
      "La tienda usa una lista de precios distinta a la que se modificó o la caché de la tienda no se ha vaciado.",
      ["Revisar qué lista de precios tiene asignada la tienda.", "Vaciar la caché de catálogo desde la integración."], "PUBLICO"],
    ["Error 502 al finalizar la compra en el checkout", ["checkout", "error-502", "pasarela", "pedidos"],
      "El cobro se hace pero el pedido no se crea y el cliente ve error 502.",
      "La pasarela tarda más de 30 segundos en responder y el balanceador corta la conexión.",
      ["Buscar el pago por referencia en la pasarela.", "Crear el pedido desde la conciliación de pagos.", "Reportar la hora exacta para revisar el monitor."], "PUBLICO"]
  ],
  VENTAS_EN_SITIO: [
    ["La impresora de facturas POS no imprime después de actualizar", ["impresora", "pos", "factura", "controlador"],
      "La factura queda registrada pero no sale el tiquete en la impresora térmica.",
      "La actualización cambió el puerto de la impresora o reinstaló el controlador genérico.",
      ["Abrir Configuración de caja > Periféricos y elegir de nuevo la impresora.", "Imprimir la página de prueba.", "Reimprimir las facturas del turno desde el historial."], "PUBLICO"],
    ["Diferencias en el cierre de caja del turno", ["cierre-caja", "turno", "efectivo", "cuadre"],
      "El cierre muestra un faltante o sobrante que no coincide con el efectivo contado.",
      "Hay ventas registradas con un medio de pago distinto al real o devoluciones sin salida de efectivo.",
      ["Sacar el reporte de ventas del turno por medio de pago.", "Revisar las devoluciones y anulaciones del turno.", "Corregir el medio de pago antes de cerrar."], "PUBLICO"],
    ["El datáfono no responde al cobrar con tarjeta", ["datafono", "tarjeta", "pagos", "conexion"],
      "La caja espera el datáfono y luego cancela la venta.",
      "El datáfono perdió la conexión con el equipo de caja o está en una red distinta.",
      ["Reiniciar el datáfono y esperar el mensaje de listo.", "Verificar que esté en la misma red del equipo de caja.", "Hacer una venta de prueba de 1.000 pesos."], "PUBLICO"],
    ["El lector de código de barras escribe caracteres extraños", ["lector-codigo", "codigo-barras", "teclado", "configuracion"],
      "Al escanear aparecen símbolos en lugar del código del producto.",
      "El lector está configurado con una distribución de teclado distinta a la del equipo.",
      ["Escanear el código de configuración 'teclado español' del manual del lector.", "Probar en el bloc de notas antes de volver a la caja."], "PUBLICO"],
    ["Cómo anular una factura POS emitida por error", ["anulacion", "factura", "nota-credito", "pos"],
      "Se facturó un producto equivocado o al cliente equivocado.",
      "Una factura electrónica no se borra: se anula con una nota crédito.",
      ["Buscar la factura en el historial de la caja.", "Generar la nota crédito total o parcial.", "Emitir la factura correcta."], "PUBLICO"],
    ["La caja queda bloqueada con un turno abierto de otro cajero", ["turno", "cajero", "bloqueo", "cierre-caja"],
      "Nadie puede abrir la caja porque otro cajero dejó su turno abierto.",
      "La caja admite un solo turno abierto y el cajero anterior no hizo el cierre.",
      ["Pedir al supervisor que cierre el turno pendiente desde Administración de cajas.", "Abrir el turno nuevo con la base de efectivo."], "PUBLICO"],
    ["Factura electrónica POS rechazada por la DIAN", ["factura-electronica", "dian", "rechazo", "resolucion"],
      "Facturas del día quedan en estado rechazado y el cliente no recibe el documento.",
      "La resolución de numeración venció o el rango de consecutivos se agotó.",
      ["Revisar la vigencia de la resolución en Configuración > Facturación.", "Cargar la nueva resolución.", "Reenviar las facturas rechazadas."], "INTERNO"],
    ["El cajón monedero no abre al registrar pago en efectivo", ["cajon-monedero", "efectivo", "impresora", "perifericos"],
      "El cajón no se abre al cobrar en efectivo.",
      "El cajón se conecta a la impresora y la orden de apertura está desactivada en el perfil de la impresora.",
      ["Activar Abrir cajón al imprimir en el perfil de la impresora.", "Revisar el cable entre el cajón y la impresora."], "PUBLICO"]
  ],
  INVENTARIOS: [
    ["Existencias negativas después de un traslado entre bodegas", ["traslado", "bodegas", "existencias", "negativos"],
      "Después de un traslado el producto queda con existencia negativa en la bodega de origen.",
      "El traslado se registró con fecha anterior a la entrada que dio la existencia.",
      ["Revisar el kardex del producto en la bodega de origen.", "Corregir la fecha del traslado o de la entrada.", "Recalcular saldos de la bodega."], "PUBLICO"],
    ["Las alertas de stock mínimo no llegan al correo", ["alerta-stock", "correo", "stock-minimo", "notificaciones"],
      "El producto baja del mínimo y nadie recibe el aviso.",
      "El usuario responsable de la bodega no tiene correo configurado o la alerta está desactivada en la bodega.",
      ["Revisar el responsable de la bodega y su correo.", "Activar Alertas de stock en la bodega.", "Revisar la carpeta de correo no deseado."], "PUBLICO"],
    ["Cómo hacer un ajuste de inventario por conteo físico", ["ajuste", "conteo-fisico", "kardex", "inventario"],
      "La existencia del sistema no coincide con lo que hay en la bodega.",
      "Pérdidas, daños o errores de registro que no pasaron por el ERP.",
      ["Crear un documento de conteo físico por bodega.", "Registrar las cantidades contadas.", "Aprobar el ajuste para que el sistema genere la diferencia."], "PUBLICO"],
    ["Código de barras duplicado en dos productos", ["codigo-barras", "productos", "duplicado", "caja"],
      "Al escanear un código aparecen dos productos.",
      "Se creó un producto nuevo copiando otro sin cambiar el código de barras.",
      ["Buscar el código en el maestro de productos.", "Asignar el código correcto al producto que quedó mal.", "Inactivar el producto si era un duplicado."], "PUBLICO"],
    ["El kardex no coincide con el saldo de la bodega", ["kardex", "saldo", "recalculo", "bodegas"],
      "El saldo del kardex y la existencia de la bodega muestran valores distintos.",
      "Un proceso de cierre se interrumpió y dejó saldos sin recalcular.",
      ["Ejecutar el recálculo de saldos del producto desde Herramientas.", "Comparar de nuevo kardex y bodega.", "Si persiste, escalar a desarrollo con el código del producto."], "INTERNO"],
    ["Recalcular el costo promedio de un producto", ["costo-promedio", "recalculo", "valorizacion", "kardex"],
      "El costo promedio sube o baja sin explicación.",
      "Una entrada se registró con costo cero o con unidad de medida equivocada.",
      ["Revisar las entradas del periodo en el kardex.", "Corregir la entrada con costo o unidad equivocada.", "Ejecutar el recálculo del costo promedio desde la fecha de la corrección."], "INTERNO"],
    ["Entrada de mercancía no suma a las existencias", ["entrada", "mercancia", "existencias", "compras"],
      "Se registra la entrada del proveedor y la existencia no cambia.",
      "La entrada quedó en borrador o se registró en otra bodega.",
      ["Buscar la entrada por número de documento.", "Confirmar la bodega de destino.", "Aprobar la entrada si quedó en borrador."], "PUBLICO"],
    ["Importar productos desde Excel sin perder códigos", ["importacion", "excel", "productos", "codigo-barras"],
      "Al importar productos los códigos que empiezan por cero pierden los ceros.",
      "Excel convierte la columna de códigos a número.",
      ["Dar formato de texto a la columna de códigos antes de pegar los datos.", "Usar la plantilla de importación del ERP.", "Revisar la vista previa antes de confirmar."], "PUBLICO"]
  ],
  COMPRAS: [
    ["La orden de compra no se puede aprobar por falta de presupuesto", ["orden-compra", "aprobacion", "presupuesto", "flujo"],
      "La orden queda pendiente de aprobación y el botón no responde.",
      "El centro de costos de la orden no tiene presupuesto disponible para el periodo.",
      ["Revisar el presupuesto del centro de costos.", "Pedir el traslado de presupuesto o cambiar el centro de costos.", "Aprobar de nuevo la orden."], "PUBLICO"],
    ["Recepción parcial de una orden de compra", ["recepcion", "orden-compra", "parcial", "inventario"],
      "Llegó solo una parte del pedido y la orden se cerró completa.",
      "Se marcó Recibir todo en lugar de registrar las cantidades recibidas.",
      ["Anular la recepción registrada.", "Registrar la recepción con las cantidades reales.", "Dejar la orden abierta para el saldo pendiente."], "PUBLICO"],
    ["Cuenta por pagar duplicada al registrar la factura del proveedor", ["cuentas-por-pagar", "factura-proveedor", "duplicado", "contabilidad"],
      "Aparecen dos cuentas por pagar con el mismo valor y la misma factura.",
      "La factura se registró desde la recepción y otra vez desde cuentas por pagar.",
      ["Identificar la cuenta por pagar sin recepción asociada.", "Anularla con su comprobante contable.", "Activar la validación de factura repetida por proveedor."], "PUBLICO"],
    ["Proveedor no aparece al crear la orden de compra", ["proveedor", "nit", "orden-compra", "terceros"],
      "El proveedor está creado pero no sale en la lista de la orden.",
      "El tercero existe pero no tiene marcado el tipo Proveedor o está inactivo.",
      ["Abrir el tercero por NIT.", "Marcar el tipo Proveedor y guardarlo activo.", "Volver a buscarlo en la orden."], "PUBLICO"],
    ["Retenciones mal calculadas en la factura del proveedor", ["retenciones", "impuestos", "factura-proveedor", "tarifas"],
      "La retención en la fuente no corresponde a la tarifa esperada.",
      "El concepto de retención del producto o del proveedor está mal parametrizado.",
      ["Revisar el concepto de retención del producto.", "Revisar la responsabilidad tributaria del proveedor.", "Recalcular la factura antes de contabilizar."], "INTERNO"],
    ["Cómo programar pagos a proveedores por lotes", ["pago-proveedor", "lotes", "tesoreria", "cuentas-por-pagar"],
      "Tesorería paga una por una las cuentas por pagar.",
      "No se usa el lote de pagos que agrupa cuentas por fecha de vencimiento.",
      ["Filtrar cuentas por pagar por fecha de vencimiento.", "Seleccionarlas y crear el lote.", "Exportar el archivo plano para el banco."], "PUBLICO"],
    ["Diferencia entre el valor de la orden y la factura recibida", ["conciliacion", "orden-compra", "factura-proveedor", "diferencias"],
      "La factura del proveedor no coincide con el valor de la orden.",
      "Cambió el precio del proveedor o llegaron cantidades distintas.",
      ["Comparar línea por línea orden, recepción y factura.", "Registrar la diferencia con la autorización del jefe de compras.", "Actualizar el precio del proveedor si el cambio es permanente."], "INTERNO"],
    ["Anular una orden de compra ya recibida", ["anulacion", "orden-compra", "recepcion", "devolucion"],
      "Se necesita anular una orden que ya tiene recepción.",
      "El sistema no anula órdenes con movimientos de inventario.",
      ["Registrar la devolución al proveedor.", "Anular la recepción.", "Anular la orden de compra."], "PUBLICO"]
  ],
  PRODUCCION: [
    ["El costo del lote no incluye la mano de obra", ["costo-lote", "mano-obra", "costos", "cierre-lote"],
      "Al cerrar el lote el costo solo suma la materia prima.",
      "La receta no tiene asociados los centros de trabajo con su tarifa por hora.",
      ["Agregar los centros de trabajo a la receta.", "Registrar las horas reales del lote.", "Volver a cerrar el lote."], "PUBLICO"],
    ["La receta descuenta materia prima de la bodega equivocada", ["receta", "materia-prima", "bodega", "consumo"],
      "La orden consume insumos de la bodega de producto terminado.",
      "La orden de producción toma la bodega por defecto del usuario y no la de la receta.",
      ["Definir la bodega de consumo en la receta.", "Revertir el consumo equivocado con un traslado.", "Crear la siguiente orden desde la receta corregida."], "PUBLICO"],
    ["Lectura de temperatura del sensor fuera de rango en el mezclado", ["sensor", "temperatura", "mezclado", "calibracion"],
      "El ERP registra una temperatura distinta a la del termómetro de planta.",
      "El sensor necesita calibración o la escala configurada no es la correcta.",
      ["Comparar la lectura del sensor con un termómetro patrón.", "Revisar la escala configurada para la máquina.", "Programar la calibración con mantenimiento."], "PUBLICO"],
    ["Cerrar un lote con unidades defectuosas en control de calidad", ["lote", "control-calidad", "defectuosos", "cierre-lote"],
      "Control de calidad rechazó unidades y el lote no se puede cerrar.",
      "Las unidades rechazadas deben salir a la bodega de no conformes antes del cierre.",
      ["Registrar las unidades rechazadas en control de calidad.", "Trasladarlas a la bodega de no conformes.", "Cerrar el lote con las unidades buenas."], "PUBLICO"],
    ["La orden de producción queda en fase de empaque y no avanza", ["orden-produccion", "empaque", "fase", "almacenamiento"],
      "La orden no pasa de empaque a almacenamiento aunque el lote está listo.",
      "Falta registrar el material de empaque consumido en la fase.",
      ["Registrar el consumo de material de empaque.", "Confirmar la fase de empaque.", "Pasar a almacenamiento."], "PUBLICO"],
    ["Configurar una máquina nueva en el módulo de producción", ["maquinaria", "configuracion", "sensor", "centros-trabajo"],
      "La planta compró una máquina y hay que registrarla.",
      "Cada máquina necesita su código, su centro de trabajo y, si tiene sensor, el identificador del dispositivo.",
      ["Crear la máquina con su código interno.", "Asociarla a un centro de trabajo.", "Registrar el identificador del sensor y probar la primera lectura."], "INTERNO"],
    ["Trazabilidad de un lote desde la materia prima", ["trazabilidad", "lote", "materia-prima", "auditoria"],
      "Un cliente reclama un producto y hay que saber qué insumos usó su lote.",
      "La trazabilidad une el lote terminado con los lotes de materia prima consumidos.",
      ["Abrir el lote terminado.", "Consultar la pestaña de consumos.", "Exportar el informe de trazabilidad."], "PUBLICO"],
    ["El sensor deja de enviar datos al ERP", ["sensor", "integracion", "maquinaria", "conexion"],
      "El ERP no recibe lecturas del sensor desde hace horas.",
      "El servicio de integración de la planta se detuvo o cambió la IP del sensor.",
      ["Revisar el estado del servicio de integración en el servidor de planta.", "Confirmar la IP del sensor.", "Reiniciar el servicio y verificar la última lectura."], "INTERNO"]
  ]
};

// Vectores sintéticos: un centro por módulo más ruido, normalizados.
// Sirven para probar el índice vectorial; no representan el texto.
const centros = Object.fromEntries(MODULOS.map(m => [m, Array.from({ length: 768 }, () => rnd() * 2 - 1)]));
const vectorSintetico = modulo => {
  const v = centros[modulo].map(x => x + (rnd() * 2 - 1) * 0.6);
  const norma = Math.sqrt(v.reduce((s, x) => s + x * x, 0));
  return v.map(x => Double(x / norma));
};

const resueltosPorModulo = {};
for (const t of tickets) {
  if (t.tipo === "INCIDENTE" && (t.estado === "RESUELTO" || t.estado === "CERRADO")) {
    (resueltosPorModulo[t.modulo] = resueltosPorModulo[t.modulo] || []).push(t);
  }
}
const autores = agentes.filter(a => !a.inactivoDesde).map(a => a.doc);
const articulos = [];
let conVector = 0;
for (const modulo of MODULOS) {
  ARTICULOS[modulo].forEach(([titulo, etiquetas, sintoma, causa, pasosA, visibilidad], k) => {
    const creado = masD(fecha(2025, 11, 10, 15), entre(0, 300));
    const actualizado = prob(0.4) ? menor(masD(creado, entre(5, 60)), masD(CORTE, -1)) : creado;
    const contenido = `Síntoma: ${sintoma}\n\nCausa: ${causa}\n\nSolución:\n` + pasosA.map((p, n) => `${n + 1}. ${p}`).join("\n");
    const candidatos = (resueltosPorModulo[modulo] || []).filter(t => t.titulo.split(" ").some(w => w.length > 5 && titulo.toLowerCase().includes(w.toLowerCase())));
    const pool = candidatos.length >= 5 ? candidatos : (resueltosPorModulo[modulo] || []);
    const ticketIds = [...new Set(mezclar(pool).slice(0, entre(0, 30)).map(t => t._id))];
    const vector = (k % 2 === 0);   // la mitad con vector sintético
    if (vector) conVector++;
    articulos.push(Object.assign({ _id: oid(creado, "09") }, comunes(creado), {
      titulo, contenido, modulo, etiquetas, visibilidad, ticketIds,
      autorId: elegir(autores)._id,
      embedding: vector ? vectorSintetico(modulo) : null,
      embeddingModelo: vector ? "sintetico/768" : null
    }, { fechaActualizacion: actualizado }));
  });
}

// ---------------------------------------------------------------------
// 10. Sesiones (refreshTokens). Usan la hora de la carga: 20 vigentes y
// 5 vencidas, que el índice TTL borra en el siguiente ciclo (60 segundos).
// ---------------------------------------------------------------------
const ahora = new Date();
const hex64 = () => Array.from({ length: 64 }, () => "0123456789abcdef"[Math.floor(rnd() * 16)]).join("");
const activosHoy = usuarios.filter(u => u.activo);
const refreshTokens = [];
for (let k = 0; k < 25; k++) {
  const vencido = k >= 20;
  const expira = vencido ? new Date(ahora.getTime() - entre(1, 48) * H) : new Date(ahora.getTime() + entre(1, 168) * H);
  const creado = new Date(expira.getTime() - 7 * D);
  refreshTokens.push(Object.assign({ _id: ObjectId() }, comunes(creado), {
    usuarioId: elegir(activosHoy)._id, tokenHash: hex64(), expiraEn: expira
  }));
}

// ---------------------------------------------------------------------
// 11. Carga
// ---------------------------------------------------------------------
const CARGA = [
  ["roles", roles], ["planes", planes], ["empresas", empresas], ["usuarios", usuarios],
  ["membresias", membresias], ["pagos", pagos], ["solicitudesCapacitacion", solicitudes],
  ["capacitaciones", capacitaciones], ["inscripciones", inscripciones], ["tickets", tickets],
  ["comentariosTicket", comentarios], ["articulos", articulos], ["refreshTokens", refreshTokens]
];

print(`\nBase: ${BD} · servidor ${base.version()}`);
print("Borrando documentos anteriores (validadores e índices se conservan)...");
for (const c of COLECCIONES.slice().reverse()) base.getCollection(c).deleteMany({});

let errores = 0;
for (const [nombre, docs] of CARGA) {
  const col = base.getCollection(nombre);
  let insertados = 0;
  for (let k = 0; k < docs.length; k += 1000) {
    const lote = docs.slice(k, k + 1000);
    try {
      insertados += Object.keys(col.insertMany(lote, { ordered: true }).insertedIds).length;
    } catch (e) {
      errores++;
      const w = (e.writeErrors && e.writeErrors[0]) || e;
      print(`  !  ${nombre}: ${e.code || ""} ${e.message}`);
      const info = w.errInfo || (w.err && w.err.errInfo);
      if (info) print(JSON.stringify(info.details || info).slice(0, 1500));
      break;
    }
  }
  print(`  ${nombre.padEnd(24)} ${String(insertados).padStart(6)} / ${docs.length}`);
}

// Resumen para la demo y para la tarea 1.6
const cuenta = (arr, f) => arr.filter(f).length;
print(`\nTickets: ${tickets.length} · abiertos (ABIERTO o EN_PROCESO): ${cuenta(tickets, t => t.estado === "ABIERTO" || t.estado === "EN_PROCESO")} · sugerencias: ${cuenta(tickets, t => t.tipo === "SUGERENCIA")}`);
print(`Membresías: ACTIVA ${cuenta(membresias, m => m.estado === "ACTIVA")} (vencidas ${cuenta(membresias, m => m.estado === "ACTIVA" && m.fechaVencimiento < CORTE)}) · SUSPENDIDA ${cuenta(membresias, m => m.estado === "SUSPENDIDA")} · PENDIENTE ${cuenta(membresias, m => m.estado === "PENDIENTE")}`);
print(`Pagos: ${pagos.length} · fallidos: ${cuenta(pagos, p => p.resultado === "FALLIDO")}`);
print(`Inscripciones: ${inscripciones.length} · asistió: ${cuenta(inscripciones, i => i.asistio === true)} · no asistió: ${cuenta(inscripciones, i => i.asistio === false)} · sin marcar: ${cuenta(inscripciones, i => i.asistio === null)}`);
print(`Artículos: ${articulos.length} · con vector sintético: ${conVector} · públicos: ${cuenta(articulos, a => a.visibilidad === "PUBLICO")}`);
print(`Contraseña de todos los usuarios: Helpdesk2026* · ADMIN: ${admin1.email}`);
print(`Tiempo: ${((Date.now() - inicioCarga) / 1000).toFixed(1)} s`);
print(errores === 0 ? "RESULTADO: OK" : "RESULTADO: REVISAR los mensajes de arriba");

})();
