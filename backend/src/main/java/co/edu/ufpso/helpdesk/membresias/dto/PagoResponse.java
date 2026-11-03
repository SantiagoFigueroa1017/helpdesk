package co.edu.ufpso.helpdesk.membresias.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Un pago en las respuestas de la API (HU18). */
public record PagoResponse(String id, String membresiaId, String empresaId, BigDecimal monto, Instant fechaPago,
        String metodoPago, String referencia, String resultado, String registradoPorId, Instant fechaCreacion,
        Instant fechaActualizacion) {
}
