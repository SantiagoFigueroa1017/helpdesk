package co.edu.ufpso.helpdesk.conocimiento.service;

import java.util.List;

/**
 * Quien convierte texto en un vector de 768 valores (P17: Gemini {@code gemini-embedding-001}). Es una
 * interfaz para que las pruebas usen un proveedor falso y no gasten la cuota de Gemini.
 */
public interface ProveedorEmbeddings {

    /** Nombre que se guarda en {@code articulos.embeddingModelo}, por ejemplo {@code gemini-embedding-001/768}. */
    String modelo();

    /** false si falta la clave: no vale la pena llamar. */
    boolean disponible();

    /**
     * Vector de un artículo ({@code RETRIEVAL_DOCUMENT}).
     *
     * @throws EmbeddingNoDisponibleException si no hay clave o Gemini falla
     */
    List<Double> documento(String texto);

    /**
     * Vector de una búsqueda ({@code RETRIEVAL_QUERY}).
     *
     * @throws EmbeddingNoDisponibleException si no hay clave o Gemini falla
     */
    List<Double> consulta(String texto);
}
