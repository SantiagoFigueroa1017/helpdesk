package co.edu.ufpso.helpdesk.empresas.domain;

/**
 * Persona de contacto de la empresa, embebida en {@code empresas.contacto}. Es lo único que edita
 * el CLIENTE_ADMIN (HU03, permiso {@code EMPRESAS_EDITAR_CONTACTO}).
 *
 * @param nombre    obligatorio, máximo 150
 * @param email     obligatorio, máximo 254
 * @param telefono  obligatorio, 7 a 20 caracteres: dígitos, {@code +}, paréntesis, espacios y guiones
 * @param direccion opcional, máximo 200. Si es null no se escribe: el validador no acepta null
 * @param ciudad    opcional, máximo 100. Igual que {@code direccion}
 */
public record Contacto(String nombre, String email, String telefono, String direccion, String ciudad) {
}
