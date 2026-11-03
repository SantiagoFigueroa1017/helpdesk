package co.edu.ufpso.helpdesk.empresas.dto;

import java.time.Instant;

/** Una empresa en las respuestas de la API (HU02). {@code direccion} y {@code ciudad} pueden ser null. */
public record EmpresaResponse(String id, String nit, String razonSocial, Contacto contacto, Instant fechaCreacion,
        Instant fechaActualizacion) {

    public record Contacto(String nombre, String email, String telefono, String direccion, String ciudad) {
    }
}
