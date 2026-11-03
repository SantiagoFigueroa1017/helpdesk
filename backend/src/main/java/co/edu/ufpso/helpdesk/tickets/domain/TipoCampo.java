package co.edu.ufpso.helpdesk.tickets.domain;

/**
 * Tipo de un campo del {@code detalle}. Le dice al frontend qué control dibujar y al backend cómo
 * convertir el valor que llega en el JSON.
 */
public enum TipoCampo {
    /** Texto de una línea. Se guarda sin espacios al inicio ni al final. */
    TEXTO,
    /** Texto de varias líneas (por ejemplo un log). Se guarda tal cual llega. */
    TEXTO_LARGO,
    /** Número entero de 32 bits. Se guarda como int. */
    ENTERO,
    /** Número con decimales. Se guarda como double. */
    DECIMAL,
    /** Dinero: hasta 12 enteros y 2 decimales, no negativo. Se guarda como Decimal128. */
    DINERO,
    /** Uno de los valores de {@link CampoDetalle#opciones()}. */
    OPCION
}
