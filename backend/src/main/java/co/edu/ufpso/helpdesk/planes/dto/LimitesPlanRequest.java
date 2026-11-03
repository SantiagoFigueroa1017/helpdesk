package co.edu.ufpso.helpdesk.planes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Límites del plan en las peticiones de HU09 y HU11. Mismos topes que el validador de
 * {@code planes.limites} (modelo 1.2, sección 4).
 */
public record LimitesPlanRequest(
        @NotNull(message = "Las horas de respuesta son obligatorias.")
        @Min(value = 1, message = "Las horas de respuesta deben ser mayores que cero.")
        Integer horasRespuesta,

        @NotNull(message = "Las capacitaciones incluidas son obligatorias.")
        @Min(value = 0, message = "Las capacitaciones incluidas no pueden ser negativas.")
        Integer capacitacionesIncluidas,

        @NotNull(message = "El máximo de usuarios es obligatorio.")
        @Min(value = 1, message = "El máximo de usuarios debe ser mayor que cero.")
        Integer maxUsuarios,

        @NotNull(message = "El máximo de módulos es obligatorio.")
        @Min(value = 1, message = "El máximo de módulos va de 1 a 5.")
        @Max(value = 5, message = "El máximo de módulos va de 1 a 5.")
        Integer maxModulos) {
}
