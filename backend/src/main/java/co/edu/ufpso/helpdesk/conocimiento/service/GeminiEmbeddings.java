package co.edu.ufpso.helpdesk.conocimiento.service;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.service.EmbeddingNoDisponibleException.Motivo;

/**
 * Cliente de Gemini {@code gemini-embedding-001} con {@code RestClient} de Spring (decidido en 2.7, sin
 * dependencias nuevas). Una llamada por texto a
 * {@code POST {urlBase}/models/gemini-embedding-001:embedContent}, con la clave en la cabecera
 * {@code x-goog-api-key} (nunca en la URL, para que no aparezca en logs ni en trazas).
 *
 * <p>Cuerpo: {@code taskType} ({@code RETRIEVAL_DOCUMENT} o {@code RETRIEVAL_QUERY}) y
 * {@code outputDimensionality: 768}. El vector no se normaliza: la similitud del índice es {@code cosine},
 * que no depende del largo del vector (modelo 1.2).
 *
 * <p>Un 429 (cuota del nivel gratuito agotada) se informa como {@link Motivo#LIMITE_ALCANZADO}; cualquier
 * otro fallo, como {@link Motivo#ERROR}. Ninguno llega al cliente de la API.
 */
@Component
@EnableConfigurationProperties(PropiedadesGemini.class)
public class GeminiEmbeddings implements ProveedorEmbeddings {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiEmbeddings.class);

    public static final String MODELO_API = "gemini-embedding-001";
    /** Valor de {@code articulos.embeddingModelo}. Un artículo con otro valor se regenera. */
    public static final String MODELO = MODELO_API + "/" + Articulo.DIMENSIONES;

    private final RestClient cliente;
    private final String clave;

    public GeminiEmbeddings(PropiedadesGemini propiedades) {
        this.clave = propiedades.clave() == null ? "" : propiedades.clave().strip();
        HttpClient http = HttpClient.newBuilder().connectTimeout(propiedades.tiempoConexion()).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(propiedades.tiempoRespuesta());
        this.cliente = RestClient.builder().baseUrl(propiedades.urlBase()).requestFactory(fabrica).build();
        if (clave.isEmpty()) {
            LOG.warn("GEMINI_API_KEY está vacía: los artículos quedan con el vector pendiente y la búsqueda por "
                    + "significado responde con la búsqueda por texto.");
        }
    }

    @Override
    public String modelo() {
        return MODELO;
    }

    @Override
    public boolean disponible() {
        return !clave.isEmpty();
    }

    @Override
    public List<Double> documento(String texto) {
        return generar(texto, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public List<Double> consulta(String texto) {
        return generar(texto, "RETRIEVAL_QUERY");
    }

    private List<Double> generar(String texto, String tarea) {
        if (!disponible()) {
            throw new EmbeddingNoDisponibleException(Motivo.NO_CONFIGURADO, "Falta GEMINI_API_KEY.");
        }
        Peticion cuerpo = new Peticion("models/" + MODELO_API, new Contenido(List.of(new Parte(texto))), tarea,
                Articulo.DIMENSIONES);
        Respuesta respuesta;
        try {
            respuesta = cliente.post()
                    .uri("/models/{modelo}:embedContent", MODELO_API)
                    .header("x-goog-api-key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                LOG.warn("Gemini respondió 429: cuota del nivel gratuito agotada por ahora.");
                throw new EmbeddingNoDisponibleException(Motivo.LIMITE_ALCANZADO, "Gemini respondió 429.", e);
            }
            // El cuerpo del error de Gemini no lleva la clave; se registra el estado para diagnosticar.
            LOG.warn("Gemini respondió {} al generar un vector.", e.getStatusCode().value());
            throw new EmbeddingNoDisponibleException(Motivo.ERROR, "Gemini respondió " + e.getStatusCode().value() + ".", e);
        } catch (RestClientException e) {
            LOG.warn("No se pudo llamar a Gemini: {}", e.getClass().getSimpleName());
            throw new EmbeddingNoDisponibleException(Motivo.ERROR, "No se pudo llamar a Gemini.", e);
        }
        List<Double> valores = respuesta == null ? null : respuesta.valores();
        if (valores == null || valores.size() != Articulo.DIMENSIONES || valores.stream().anyMatch(Objects::isNull)) {
            int recibidos = valores == null ? 0 : valores.size();
            LOG.warn("Gemini devolvió un vector de {} valores; se esperaban {}.", recibidos, Articulo.DIMENSIONES);
            throw new EmbeddingNoDisponibleException(Motivo.ERROR, "Vector con dimensiones inesperadas.");
        }
        return List.copyOf(valores);
    }

    // Formato JSON de embedContent. Los nombres de los componentes son los campos que espera Gemini.

    public record Peticion(String model, Contenido content, String taskType, int outputDimensionality) {
    }

    public record Contenido(List<Parte> parts) {
    }

    public record Parte(String text) {
    }

    public record Valores(List<Double> values) {
    }

    /**
     * {@code embedContent} responde {@code {"embedding": {"values": [...]}}}. Se acepta también la forma
     * en lista ({@code embeddings}) que usan los SDK, por si la API la devuelve.
     */
    public record Respuesta(Valores embedding, List<Valores> embeddings) {

        public List<Double> valores() {
            if (embedding != null) {
                return embedding.values();
            }
            if (embeddings != null && !embeddings.isEmpty()) {
                return embeddings.get(0).values();
            }
            return null;
        }
    }
}
