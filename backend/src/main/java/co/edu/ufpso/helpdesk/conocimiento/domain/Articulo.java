package co.edu.ufpso.helpdesk.conocimiento.domain;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Documento de la colección {@code articulos} (HU29 a HU32). Modelo 1.2, sección 9.
 *
 * <ul>
 *   <li>{@code etiquetas}: hasta 20, sin repetidas, cada una de hasta 50 caracteres.</li>
 *   <li>{@code ticketIds}: tickets que resolvió, hasta 500 y sin repetidos. El vínculo vive solo
 *       aquí: HU30 lo agrega con {@code $addToSet} y HU24 lo quita con {@code $pull}.</li>
 *   <li>{@code embedding} y {@code embeddingModelo} van juntos (oneOf del validador): los dos en
 *       null mientras el vector está pendiente, o 768 doubles y el nombre del modelo. Son
 *       obligatorios aunque valgan null, por eso llevan {@code @Field(write = ALWAYS)}. No tienen
 *       setters sueltos: se cambian con {@link #asignarEmbedding} y
 *       {@link #marcarEmbeddingPendiente}.</li>
 * </ul>
 */
@Document("articulos")
public class Articulo extends DocumentoBase {

    /** Dimensiones del vector (Gemini {@code gemini-embedding-001} con {@code output_dimensionality} 768). */
    public static final int DIMENSIONES = 768;

    /** Tope de {@code ticketIds} en el validador (1.3). */
    public static final int MAX_TICKETS = 500;

    private String titulo;
    private String contenido;
    private ModuloErp modulo;
    private List<String> etiquetas = new ArrayList<>();
    private Visibilidad visibilidad = Visibilidad.INTERNO;
    private List<ObjectId> ticketIds = new ArrayList<>();
    private ObjectId autorId;

    @Field(write = Field.Write.ALWAYS)
    private List<Double> embedding;

    @Field(write = Field.Write.ALWAYS)
    private String embeddingModelo;

    public Articulo() {
    }

    /** Guarda el vector y el modelo que lo generó, siempre juntos. */
    public void asignarEmbedding(List<Double> vector, String modelo) {
        this.embedding = vector;
        this.embeddingModelo = modelo;
    }

    /**
     * Deja el vector pendiente (los dos campos en null). Se usa al crear, cuando Gemini falla y
     * cuando cambian el título, las etiquetas o el contenido (HU31) y Gemini no responde. La tarea
     * programada {@code RegeneracionEmbeddings} lo reintenta.
     */
    public void marcarEmbeddingPendiente() {
        this.embedding = null;
        this.embeddingModelo = null;
    }

    /** true si el vector todavía no se ha generado. */
    public boolean embeddingPendiente() {
        return embedding == null;
    }

    /**
     * Comprueba la regla del oneOf del validador: los dos en null, o un vector de 768 valores con
     * el nombre del modelo. El servicio la revisa antes de guardar.
     */
    public boolean embeddingCoherente() {
        if (embedding == null) {
            return embeddingModelo == null;
        }
        return embedding.size() == DIMENSIONES
                && embedding.stream().allMatch(v -> v != null)
                && embeddingModelo != null && !embeddingModelo.isBlank();
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public ModuloErp getModulo() {
        return modulo;
    }

    public void setModulo(ModuloErp modulo) {
        this.modulo = modulo;
    }

    public List<String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(List<String> etiquetas) {
        this.etiquetas = etiquetas;
    }

    public Visibilidad getVisibilidad() {
        return visibilidad;
    }

    public void setVisibilidad(Visibilidad visibilidad) {
        this.visibilidad = visibilidad;
    }

    public List<ObjectId> getTicketIds() {
        return ticketIds;
    }

    public void setTicketIds(List<ObjectId> ticketIds) {
        this.ticketIds = ticketIds;
    }

    public ObjectId getAutorId() {
        return autorId;
    }

    public void setAutorId(ObjectId autorId) {
        this.autorId = autorId;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public String getEmbeddingModelo() {
        return embeddingModelo;
    }
}
