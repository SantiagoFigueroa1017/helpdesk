/**
 * Membresías (HU13 a HU16) y pagos (HU17 a HU20).
 * <p>Colecciones: {@code membresias} y {@code pagos}. Aquí vive la transacción que, al
 * registrar un pago fallido, inserta el pago y suspende la membresía, y la verificación
 * central de soporte de la sección 7.5 del contexto.
 *
 * <p>Subpaquetes (se crean con su primera clase): {@code controller}, {@code service},
 * {@code repository}, {@code domain}, {@code dto} y {@code mapper}. Otro módulo solo
 * entra por el {@code service}; nunca usa este {@code repository} ni este {@code domain}.
 */
package co.edu.ufpso.helpdesk.membresias;
