package co.edu.ufpso.helpdesk.common.dominio;

/**
 * Alcance de un rol (contexto 8.4).
 *
 * <p>GLOBAL: personal de Sistemas Integrados, trabaja con datos de todas las empresas.
 * EMPRESA: usuarios de una empresa cliente; toda consulta se filtra por su {@code empresaId} y
 * les aplica el bloqueo por falta de pago (7.5). Todos los roles de un usuario tienen el mismo
 * alcance.
 */
public enum AlcanceRol {
    GLOBAL,
    EMPRESA
}
