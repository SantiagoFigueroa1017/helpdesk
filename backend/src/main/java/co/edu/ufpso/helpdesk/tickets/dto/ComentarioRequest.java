package co.edu.ufpso.helpdesk.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo para crear o editar un comentario de ticket (fuera de las 40). */
public record ComentarioRequest(
        @NotBlank(message = "El comentario no puede estar vacío.")
        @Size(max = 5000, message = "El comentario admite como máximo 5000 caracteres.")
        String texto) {
}
