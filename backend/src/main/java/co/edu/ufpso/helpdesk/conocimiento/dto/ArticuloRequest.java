package co.edu.ufpso.helpdesk.conocimiento.dto;

import java.util.List;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.conocimiento.domain.Visibilidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/articulos} (HU29) y de {@code PUT /api/articulos/{id}} (HU31). En el PUT
 * reemplaza el artículo completo: lo que no llega queda vacío (etiquetas y tickets) o con su valor por
 * defecto (visibilidad INTERNO).
 *
 * <p>Las longitudes son las del validador de {@code articulos} (1.3). Las etiquetas se guardan sin espacios
 * sobrantes y sin repetidas; los tickets, sin repetidos.
 *
 * @param visibilidad PUBLICO o INTERNO; vacía, INTERNO (modelo 1.2)
 * @param ticketIds   tickets que el artículo ayudó a resolver (HU29). Deben existir
 */
public record ArticuloRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 200, message = "El título admite como máximo 200 caracteres.")
        String titulo,

        @NotBlank(message = "El contenido es obligatorio.")
        @Size(max = 50000, message = "El contenido admite como máximo 50000 caracteres.")
        String contenido,

        @NotNull(message = "El módulo es obligatorio.")
        ModuloErp modulo,

        @Size(max = 20, message = "Un artículo admite como máximo 20 etiquetas.")
        List<@NotBlank(message = "Una etiqueta no puede estar vacía.")
             @Size(max = 50, message = "Cada etiqueta admite como máximo 50 caracteres.") String> etiquetas,

        Visibilidad visibilidad,

        @Size(max = 500, message = "Un artículo admite como máximo 500 tickets vinculados.")
        List<@NotNull(message = "Un id de ticket no puede ser nulo.")
             @Pattern(regexp = Ids.PATRON, message = "Un id de ticket no es válido.") String> ticketIds) {
}
