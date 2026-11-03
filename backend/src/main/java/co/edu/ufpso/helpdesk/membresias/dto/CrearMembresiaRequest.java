package co.edu.ufpso.helpdesk.membresias.dto;

import java.time.Instant;
import java.util.Set;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/membresias} (HU13). La membresía nace PENDIENTE: el soporte empieza con
 * el primer pago aprobado. Las reglas que consultan otras colecciones (empresa, plan activo,
 * {@code maxModulos}) y la comparación de fechas las revisa el servicio.
 */
public record CrearMembresiaRequest(
        @NotBlank(message = "La empresa es obligatoria.")
        @Pattern(regexp = Ids.PATRON, message = "El id de la empresa no es válido.")
        String empresaId,

        @NotBlank(message = "El plan es obligatorio.")
        @Pattern(regexp = Ids.PATRON, message = "El id del plan no es válido.")
        String planId,

        @NotEmpty(message = "Debe contratar al menos un módulo.")
        @Size(max = 5, message = "Se pueden contratar como máximo 5 módulos.")
        Set<@NotNull(message = "Hay un módulo vacío en la lista.") ModuloErp> modulosContratados,

        @NotNull(message = "La fecha de inicio es obligatoria.")
        Instant fechaInicio,

        @NotNull(message = "La fecha de vencimiento es obligatoria.")
        Instant fechaVencimiento) {
}
