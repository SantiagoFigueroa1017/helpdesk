/**
 * Base de conocimientos (HU29 a HU32): búsqueda por texto en español y búsqueda
 * por significado con Atlas Vector Search y Gemini.
 * <p>Colección: {@code articulos}. Sin {@code ARTICULOS_VER_INTERNOS}, un artículo
 * interno responde 404.
 *
 * <p>Tarea 2.7: {@code ArticuloService} (CRUD y vínculo con tickets),
 * {@code BusquedaArticulosService} (las dos búsquedas), {@code GeminiEmbeddings} (cliente REST de
 * Gemini) y {@code RegeneracionEmbeddings} (tarea programada de vectores pendientes). Usa el módulo
 * de tickets solo por {@code TicketsExistentesService} y el de usuarios por
 * {@code NombresUsuarioService}.
 *
 * <p>Borrado (HU32): físico y sin cascada; el vínculo con tickets vive solo en el artículo.
 *
 * <p>Subpaquetes (se crean con su primera clase): {@code controller}, {@code service},
 * {@code repository}, {@code domain}, {@code dto} y {@code mapper}. Otro módulo solo
 * entra por el {@code service}; nunca usa este {@code repository} ni este {@code domain}.
 */
package co.edu.ufpso.helpdesk.conocimiento;
