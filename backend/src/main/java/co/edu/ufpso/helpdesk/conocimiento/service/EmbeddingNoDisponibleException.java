package co.edu.ufpso.helpdesk.conocimiento.service;

/**
 * Gemini no entregó el vector. No llega al cliente: quien la recibe guarda el artículo con el vector
 * pendiente o responde la búsqueda por significado con la de texto (modelo 1.2, "Embeddings").
 */
public class EmbeddingNoDisponibleException extends RuntimeException {

    /** Por qué no hubo vector. Sale en la respuesta de búsqueda como {@code aviso}. */
    public enum Motivo {
        /** {@code GEMINI_API_KEY} vacía. */
        NO_CONFIGURADO,
        /** Gemini respondió 429: se acabó la cuota del nivel gratuito por minuto o por día. */
        LIMITE_ALCANZADO,
        /** Error de red, tiempo agotado u otra respuesta de error. */
        ERROR
    }

    private final Motivo motivo;

    public EmbeddingNoDisponibleException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public EmbeddingNoDisponibleException(Motivo motivo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
