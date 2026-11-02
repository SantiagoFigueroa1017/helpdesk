/**
 * Código compartido por todos los módulos y sin reglas de negocio propias.
 * <p>{@code common.error}: formato único de error (ProblemDetail, RFC 9457) y excepciones
 * que lanzan los servicios.
 * <p>{@code common.paginacion}: respuesta {@code Pagina} de todo listado y reglas de
 * tamaño y orden. La auditoría llegará aquí cuando haga falta.
 * <p>{@code common.dominio}: tipos que comparten varios módulos, como {@code ModuloErp}. Viven
 * aquí porque un módulo no puede usar el {@code domain} de otro.
 */
package co.edu.ufpso.helpdesk.common;
