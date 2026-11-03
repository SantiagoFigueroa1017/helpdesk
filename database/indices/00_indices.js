// =====================================================================
// 00_indices.js · Índices de la base helpdesk (tarea 1.4)
//
// Crea los 33 índices normales del modelo (únicos, compuestos, multikey,
// de texto y TTL): los 32 de 1.4 y ix_articulos_modulo_fecha, agregado
// en 2.7 para el listado de artículos. El índice vectorial va aparte en
// 01_indice_vectorial_atlas.js porque solo existe en Atlas.
//
// Cómo se usa: pegar el archivo completo en la consola mongosh de Compass,
// conectada a Atlas o a local. Se puede correr varias veces: si el índice
// ya existe con el mismo nombre y la misma definición, MongoDB no hace nada.
//
// Fuente: modelo/MODELO_DATOS_1.2.md, sección "Resumen de índices".
// Convención de nombres:
//   ux_  índice único
//   ix_  índice normal (simple, compuesto o multikey)
//   txt_ índice de texto
//   ttl_ índice con expiración
// El nombre aparece en explain() como indexName, así que en la
// sustentación se lee directo qué índice usó cada consulta.
// =====================================================================

// Todo va dentro de una función para que el script se pueda pegar varias
// veces en la misma consola sin el error "Identifier has already been declared".
(() => {

const BD = "helpdesk";
const base = db.getSiblingDB(BD);

const INDICES = {
  empresas: [
    { name: "ux_empresas_nit", key: { nit: 1 }, opciones: { unique: true }, atiende: "HU01, HU03: NIT único" },
    { name: "ix_empresas_razonSocial", key: { razonSocial: 1 }, atiende: "HU02: buscar y ordenar por razón social" }
  ],
  roles: [
    { name: "ux_roles_nombre", key: { nombre: 1 }, opciones: { unique: true }, atiende: "HU25: nombre único" }
  ],
  usuarios: [
    { name: "ux_usuarios_email", key: { email: 1 }, opciones: { unique: true }, atiende: "Login, HU05: correo único" },
    { name: "ix_usuarios_rolIds", key: { rolIds: 1 }, atiende: "HU26 usuarios por rol, HU28 bloqueo de borrado" },
    { name: "ix_usuarios_empresa_nombre", key: { empresaId: 1, nombre: 1 }, atiende: "HU06 listado del CLIENTE_ADMIN, HU04" }
  ],
  planes: [
    { name: "ux_planes_nombre", key: { nombre: 1 }, opciones: { unique: true }, atiende: "HU09: nombre único" }
  ],
  membresias: [
    { name: "ux_membresias_empresa", key: { empresaId: 1 }, opciones: { unique: true }, atiende: "HU13 una por empresa, verificación de soporte" },
    { name: "ix_membresias_plan_estado", key: { planId: 1, estado: 1 }, atiende: "HU12 bloqueo de borrado, A2" }
  ],
  pagos: [
    { name: "ix_pagos_empresa_fecha", key: { empresaId: 1, fechaPago: -1 }, atiende: "HU18 historial por empresa, HU04" },
    { name: "ix_pagos_resultado_fecha", key: { resultado: 1, fechaPago: 1 }, atiende: "A5 recaudo y fallidos por mes" },
    { name: "ix_pagos_membresia", key: { membresiaId: 1 }, atiende: "HU16 bloqueo de borrado" }
  ],
  tickets: [
    { name: "ix_tickets_empresa_estado_fecha", key: { empresaId: 1, estado: 1, fechaCreacion: -1 }, atiende: "HU22 (CLIENTE_ADMIN), A4, A8, HU04" },
    { name: "ix_tickets_modulo_tipo_fecha", key: { modulo: 1, tipo: 1, fechaCreacion: -1 }, atiende: "A1, filtros de HU22" },
    { name: "ix_tickets_agente_estado_fecha", key: { agenteId: 1, estado: 1, fechaCreacion: -1 }, atiende: "Bandeja del agente, A3, HU08" },
    { name: "ix_tickets_solicitante_estado_fecha", key: { solicitanteId: 1, estado: 1, fechaCreacion: -1 }, atiende: "HU22 mis tickets (CLIENTE), HU08" },
    { name: "ix_tickets_solicitante_modulo_fecha", key: { solicitanteId: 1, modulo: 1, fechaCreacion: 1 }, atiende: "A9 incidentes antes y después" },
    { name: "ix_tickets_estado_fechaLimite", key: { estado: 1, fechaLimiteRespuesta: 1 }, atiende: "HU22 v.4 ordenar por vencimiento" },
    { name: "ix_tickets_creador", key: { creadorId: 1 }, atiende: "HU08 bloqueo de borrado" }
  ],
  comentariosTicket: [
    { name: "ix_comentarios_ticket_fecha", key: { ticketId: 1, fechaCreacion: 1 }, atiende: "Detalle del ticket, HU24 cascada" }
  ],
  articulos: [
    {
      name: "txt_articulos",
      key: { titulo: "text", etiquetas: "text", contenido: "text" },
      opciones: {
        weights: { titulo: 10, etiquetas: 5, contenido: 1 },
        default_language: "spanish"
      },
      atiende: "HU30 búsqueda por palabras (pesos decididos en 1.3)"
    },
    { name: "ix_articulos_ticketIds", key: { ticketIds: 1 }, atiende: "HU24 $pull en cascada, A7" },
    { name: "ix_articulos_modulo_fecha", key: { modulo: 1, fechaCreacion: -1 }, atiende: "HU30 listado por módulo y fecha (agregado en 2.7)" }
  ],
  capacitaciones: [
    { name: "ix_capacitaciones_fecha_modulo", key: { fechaInicio: 1, modulo: 1 }, atiende: "HU34 cronograma" },
    { name: "ix_capacitaciones_empresa_fecha", key: { empresaId: 1, fechaInicio: 1 }, atiende: "HU34 personalizadas, HU04" },
    { name: "ix_capacitaciones_instructor_fecha", key: { instructorId: 1, fechaInicio: 1 }, atiende: "HU08 bloqueo, HU39" }
  ],
  solicitudesCapacitacion: [
    { name: "ix_solicitudes_estado_fecha", key: { estado: 1, fechaCreacion: 1 }, atiende: "Bandeja del agente" },
    { name: "ix_solicitudes_empresa_fecha", key: { empresaId: 1, fechaCreacion: -1 }, atiende: "CLIENTE_ADMIN, límite del plan, HU04" }
  ],
  inscripciones: [
    { name: "ux_inscripciones_capacitacion_usuario", key: { capacitacionId: 1, usuarioId: 1 }, opciones: { unique: true }, atiende: "HU37 sin duplicados, HU38, A6, HU36" },
    { name: "ix_inscripciones_usuario_fecha", key: { usuarioId: 1, fechaCreacion: -1 }, atiende: "HU38 mis inscripciones, HU08" }
  ],
  refreshTokens: [
    { name: "ux_refresh_tokenHash", key: { tokenHash: 1 }, opciones: { unique: true }, atiende: "Rotación del refresh token" },
    { name: "ttl_refresh_expiraEn", key: { expiraEn: 1 }, opciones: { expireAfterSeconds: 0 }, atiende: "Borra sesiones vencidas" },
    { name: "ix_refresh_usuario", key: { usuarioId: 1 }, atiende: "HU08 cierre de sesiones, logout" }
  ]
};

// ---------------------------------------------------------------------
// Ejecución
// ---------------------------------------------------------------------
const existentesEnBD = base.getCollectionNames();
let creados = 0, sinCambio = 0, errores = 0, coleccionesFaltantes = 0;

print(`\nBase: ${BD} · servidor ${base.version()}\n`);

for (const [coleccion, lista] of Object.entries(INDICES)) {
  // createIndex sobre una colección inexistente la crea SIN validador
  // (contexto maestro 6.2). Por eso se salta y se avisa.
  if (!existentesEnBD.includes(coleccion)) {
    print(`[FALTA] ${coleccion}: la colección no existe. Corre primero validadores/00_todos_los_validadores.js`);
    coleccionesFaltantes++;
    continue;
  }

  const col = base.getCollection(coleccion);
  const antes = col.getIndexes().map(i => i.name);

  for (const ix of lista) {
    try {
      col.createIndex(ix.key, Object.assign({ name: ix.name }, ix.opciones || {}));
      if (antes.includes(ix.name)) {
        sinCambio++;
        print(`  =  ${coleccion}.${ix.name}`);
      } else {
        creados++;
        print(`  +  ${coleccion}.${ix.name}  (${ix.atiende})`);
      }
    } catch (e) {
      errores++;
      // 85 y 86: ya hay un índice con las mismas claves y otro nombre u otras opciones.
      print(`  !  ${coleccion}.${ix.name}: código ${e.code} · ${e.message}`);
    }
  }

  // Índices que están en la colección y no en el modelo. No se borran:
  // solo se reportan para decidir a mano.
  const esperados = new Set(lista.map(i => i.name).concat(["_id_"]));
  const sobran = col.getIndexes().map(i => i.name).filter(n => !esperados.has(n));
  if (sobran.length) print(`  ?  ${coleccion}: índices fuera del modelo: ${sobran.join(", ")}`);
}

const total = Object.values(INDICES).reduce((s, l) => s + l.length, 0);
print(`\nÍndices en el script: ${total}`);
print(`Creados: ${creados} · ya existían: ${sinCambio} · errores: ${errores} · colecciones faltantes: ${coleccionesFaltantes}`);
print(errores === 0 && coleccionesFaltantes === 0 ? "RESULTADO: OK" : "RESULTADO: REVISAR los mensajes de arriba");

})();
