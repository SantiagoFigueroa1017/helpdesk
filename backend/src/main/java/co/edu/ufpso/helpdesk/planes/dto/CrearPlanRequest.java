package co.edu.ufpso.helpdesk.planes.dto;

import java.math.BigDecimal;

import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/planes} (HU09). El plan nace activo. {@code precio} es mensual y se
 * guarda como Decimal128.
 */
public record CrearPlanRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre admite como máximo 100 caracteres.")
        String nombre,

        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 1000, message = "La descripción admite como máximo 1000 caracteres.")
        String descripcion,

        @NotNull(message = "El precio es obligatorio.")
        @DecimalMin(value = "0", inclusive = false, message = "El precio debe ser mayor que cero.")
        @Digits(integer = 12, fraction = 2, message = "El precio admite hasta 12 enteros y 2 decimales.")
        BigDecimal precio,

        @NotNull(message = "El nivel de acceso es obligatorio: BASICO, ESTANDAR o PREMIUM.")
        NivelAcceso nivelAcceso,

        @NotNull(message = "Los límites del plan son obligatorios.")
        @Valid
        LimitesPlanRequest limites) {
}
