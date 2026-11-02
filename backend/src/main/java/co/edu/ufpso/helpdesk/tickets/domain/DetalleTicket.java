package co.edu.ufpso.helpdesk.tickets.domain;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Campos específicos del módulo del ERP en un incidente: la parte polimórfica del ticket.
 *
 * <p>Las sugerencias no llevan detalle (decisión de 1.2). Cada implementación corresponde a una
 * rama del {@code oneOf} del validador de tickets y tiene los mismos campos.
 *
 * <p>Escritura: Spring Data guarda el record tal cual, sin {@code _class} (decisión de 2.3).
 * Lectura: {@code DetalleTicketLecturaConverter} reconoce el tipo por su campo exclusivo, que el
 * validador exige en su rama y prohíbe en las demás.
 */
public sealed interface DetalleTicket
        permits DetalleVentasOnline, DetalleProduccion, DetalleInventarios, DetalleCompras, DetalleVentasEnSitio {

    /** Módulo al que pertenece este detalle. Debe coincidir con {@code Ticket.modulo}. */
    ModuloErp modulo();
}
