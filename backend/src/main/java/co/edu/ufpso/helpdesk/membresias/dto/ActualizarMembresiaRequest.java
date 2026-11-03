package co.edu.ufpso.helpdesk.membresias.dto;

import java.time.Instant;
import java.util.Set;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/membresias/{id}} (HU15). La empresa no cambia. El estado lo puede
 * corregir el administrador a mano; los pagos lo cambian solos (HU17, HU19).
 */
public record ActualizarMembresiaRequest(
        @NotBlank(message = "El plan es obligatorio.")
        @Pattern(regexp = Ids.PATRON, message = "El id del plan no es válido.")
        String planId,

        @NotEmpty(message = "Debe contratar al menos un módulo.")
        @Size(max = 5, message = "Se pueden contratar como máximo 5 módulos.")
        Set<@NotNull(message = "Hay un módulo vacío en la lista.") ModuloErp> modulosContratados,

        @NotNull(message = "La fecha de inicio es obligatoria.")
        Instant fechaInicio,

        @NotNull(message = "La fecha de vencimiento es obligatoria.")
        Instant fechaVencimiento,

        @NotNull(message = "El estado es obligatorio: PENDIENTE, ACTIVA o SUSPENDIDA.")
        EstadoMembresia estado) {
}
