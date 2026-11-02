package co.edu.ufpso.helpdesk.common.error;

/**
 * Un error de validación de entrada. Va en la lista {@code errores} del ProblemDetail.
 * No incluye el valor rechazado, porque podría ser una contraseña.
 *
 * @param campo   nombre del campo del DTO o del parámetro, por ejemplo {@code contacto.email}
 * @param mensaje mensaje de la anotación de Bean Validation
 */
public record ErrorCampo(String campo, String mensaje) {
}
