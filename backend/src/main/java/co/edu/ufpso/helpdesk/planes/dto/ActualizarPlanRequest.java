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
 * Cuerpo de {@code PUT /api/planes/{id}} (HU11). Reemplaza todos los campos editables. Cambiar el
 * precio no toca los pagos ya registrados; bajar los límites no borra usuarios, módulos ni
 * capacitaciones (HU11 v.4). Un plan inactivo deja de asignarse, pero sus membresías siguen.
 */
public record ActualizarPlanRequest(
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
        LimitesPlanRequest limites,

        @NotNull(message = "El estado es obligatorio.")
        Boolean activo) {
}
