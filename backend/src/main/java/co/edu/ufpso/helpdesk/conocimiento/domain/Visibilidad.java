package co.edu.ufpso.helpdesk.conocimiento.domain;

/**
 * Quién ve un artículo (P22, DECIDIDO en 1.2). INTERNO solo lo ve quien tiene
 * {@code ARTICULOS_VER_INTERNOS}; sin ese permiso, toda consulta lleva {@code visibilidad: PUBLICO}
 * y un artículo interno responde 404.
 */
public enum Visibilidad {
    PUBLICO,
    INTERNO
}
