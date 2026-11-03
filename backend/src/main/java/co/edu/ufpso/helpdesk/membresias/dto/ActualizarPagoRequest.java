package co.edu.ufpso.helpdesk.membresias.dto;

import java.math.BigDecimal;
import java.time.Instant;

import co.edu.ufpso.helpdesk.membresias.domain.MetodoPago;
import co.edu.ufpso.helpdesk.membresias.domain.ResultadoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/pagos/{id}} (HU19). La membresía, la empresa y quien lo registró no
 * cambian. Cambiar el resultado solo afecta a la membresía si este es su pago más reciente.
 */
public record ActualizarPagoRequest(
        @NotNull(message = "El monto es obligatorio.")
        @DecimalMin(value = "0", inclusive = false, message = "El monto debe ser mayor que cero.")
        @Digits(integer = 12, fraction = 2, message = "El monto admite hasta 12 enteros y 2 decimales.")
        BigDecimal monto,

        @NotNull(message = "La fecha del pago es obligatoria.")
        Instant fechaPago,

        @NotNull(message = "El método de pago es obligatorio: TRANSFERENCIA, TARJETA, PSE, EFECTIVO o CONSIGNACION.")
        MetodoPago metodoPago,

        @NotBlank(message = "La referencia es obligatoria.")
        @Size(max = 100, message = "La referencia admite como máximo 100 caracteres.")
        String referencia,

        @NotNull(message = "El resultado es obligatorio: APROBADO o FALLIDO.")
        ResultadoPago resultado) {
}
