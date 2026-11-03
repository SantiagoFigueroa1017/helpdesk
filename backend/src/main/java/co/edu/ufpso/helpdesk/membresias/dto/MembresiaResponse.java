package co.edu.ufpso.helpdesk.membresias.dto;

import java.time.Instant;
import java.util.List;

import co.edu.ufpso.helpdesk.membresias.service.EstadoSoporte;

/**
 * Una membresía en las respuestas de la API (HU14).
 *
 * @param estado        el estado guardado: PENDIENTE, ACTIVA o SUSPENDIDA
 * @param estadoVisible el que se muestra: igual a {@code estado}, salvo una ACTIVA vencida, que sale
 *                      como VENCIDA (etiqueta derivada, no se guarda)
 * @param soporte       si la empresa tiene soporte ahora y, si no, por qué
 */
public record MembresiaResponse(String id, String empresaId, String planId, List<String> modulosContratados,
        Instant fechaInicio, Instant fechaVencimiento, String estado, String estadoVisible, EstadoSoporte soporte,
        Instant fechaCreacion, Instant fechaActualizacion) {
}
