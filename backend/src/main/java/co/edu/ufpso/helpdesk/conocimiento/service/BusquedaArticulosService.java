package co.edu.ufpso.helpdesk.conocimiento.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.MongoException;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.domain.Visibilidad;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloEncontradoResponse;
import co.edu.ufpso.helpdesk.conocimiento.dto.ResultadoBusquedaResponse;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Las dos búsquedas de HU30 (contexto 6.4, tarea 2.7). Las dos se ordenan por relevancia, se filtran por
 * módulo y, sin {@code ARTICULOS_VER_INTERNOS}, solo devuelven artículos PUBLICO.
 *
 * <p>Por palabras: índice {@code txt_articulos} (español, pesos título 10, etiquetas 5, contenido 1).
 * Funciona en Atlas y en local.
 *
 * <p>Por significado: Gemini convierte la consulta en un vector ({@code RETRIEVAL_QUERY}) y
 * {@code $vectorSearch} busca los artículos más cercanos en {@code vec_articulos_embedding}. Solo existe en
 * Atlas. Si no se puede hacer (sin clave, Gemini con 429 o caído, o perfil local), la API responde con la
 * búsqueda por texto y lo dice en {@code aviso}: el frontend sigue mostrando resultados.
 *
 * <p>Los dos pipelines se arman con el driver de MongoDB tal como se escribirían en mongosh, para poder
 * mostrarlos igual en la sustentación.
 */
@Service
public class BusquedaArticulosService {

    private static final Logger LOG = LoggerFactory.getLogger(BusquedaArticulosService.class);

    public static final String INDICE_VECTORIAL = "vec_articulos_embedding";
    public static final int LIMITE_POR_DEFECTO = 10;
    public static final int LIMITE_MAXIMO = 20;
    public static final int MAXIMO_TEXTO = 200;
    public static final int MAXIMO_CONSULTA = 500;
    /** Mínimo de palabras de la búsqueda por significado (DECIDIDO en 1.2, el frontend espera 3). */
    public static final int MINIMO_PALABRAS = 3;
    private static final int LARGO_EXTRACTO = 300;

    private final MongoTemplate template;
    private final ProveedorEmbeddings embeddings;

    /**
     * Vectores de consultas repetidas (modelo 1.2). El nivel gratuito de Gemini admite 1.000 peticiones al
     * día; la misma consulta escrita otra vez no gasta una.
     */
    private final Cache<String, List<Double>> vectoresConsulta = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public BusquedaArticulosService(MongoTemplate template, ProveedorEmbeddings embeddings) {
        this.template = template;
        this.embeddings = embeddings;
    }

    // ------------------------------------------------------------------
    // Búsqueda por palabras
    // ------------------------------------------------------------------

    public ResultadoBusquedaResponse porTexto(String texto, ModuloErp modulo, Integer limite, UsuarioAutenticado actor) {
        String limpio = requerido("texto", texto, MAXIMO_TEXTO);
        return new ResultadoBusquedaResponse("TEXTO", null, buscarTexto(limpio, modulo, limite(limite), actor));
    }

    /**
     * <pre>
     * db.articulos.aggregate([
     *   { $match: { $text: { $search: "...", $language: "spanish" }, modulo: "...", visibilidad: "PUBLICO" } },
     *   { $sort: { puntaje: { $meta: "textScore" } } },
     *   { $limit: 10 },
     *   { $project: { titulo: 1, modulo: 1, etiquetas: 1, visibilidad: 1,
     *                 extracto: { $substrCP: ["$contenido", 0, 300] }, puntaje: { $meta: "textScore" } } }
     * ])
     * </pre>
     */
    private List<ArticuloEncontradoResponse> buscarTexto(String texto, ModuloErp modulo, int limite,
            UsuarioAutenticado actor) {
        Document filtro = new Document("$text", new Document("$search", texto).append("$language", "spanish"));
        if (modulo != null) {
            filtro.append("modulo", modulo.name());
        }
        if (!actor.tiene(Permiso.ARTICULOS_VER_INTERNOS)) {
            filtro.append("visibilidad", Visibilidad.PUBLICO.name());
        }
        List<Bson> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$sort", new Document("puntaje", new Document("$meta", "textScore"))),
                new Document("$limit", limite),
                proyeccion("textScore"));
        return ejecutar(pipeline);
    }

    // ------------------------------------------------------------------
    // Búsqueda por significado
    // ------------------------------------------------------------------

    public ResultadoBusquedaResponse porSignificado(String consulta, ModuloErp modulo, Integer limite,
            UsuarioAutenticado actor) {
        String limpia = requerido("consulta", consulta, MAXIMO_CONSULTA);
        if (limpia.split("\\s+").length < MINIMO_PALABRAS) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                    "La búsqueda por significado necesita al menos " + MINIMO_PALABRAS + " palabras.");
        }
        int tope = limite(limite);

        List<Double> vector;
        try {
            vector = vectorDe(limpia);
        } catch (EmbeddingNoDisponibleException e) {
            String aviso = switch (e.getMotivo()) {
                case NO_CONFIGURADO -> "GEMINI_NO_CONFIGURADO";
                case LIMITE_ALCANZADO -> "GEMINI_LIMITE_ALCANZADO";
                case ERROR -> "GEMINI_NO_DISPONIBLE";
            };
            return new ResultadoBusquedaResponse("TEXTO", aviso, buscarTexto(limpia, modulo, tope, actor));
        }

        try {
            return new ResultadoBusquedaResponse("SEMANTICA", null, buscarVector(vector, modulo, tope, actor));
        } catch (MongoException e) {
            // En el replica set local no existe $vectorSearch (contexto 6.3).
            LOG.info("Búsqueda vectorial no disponible ({}): se responde con la de texto.", e.getClass().getSimpleName());
            return new ResultadoBusquedaResponse("TEXTO", "VECTORIAL_NO_DISPONIBLE",
                    buscarTexto(limpia, modulo, tope, actor));
        }
    }

    /**
     * <pre>
     * db.articulos.aggregate([
     *   { $vectorSearch: { index: "vec_articulos_embedding", path: "embedding", queryVector: [768 valores],
     *                      numCandidates: 200, limit: 30,
     *                      filter: { $and: [ { modulo: { $eq: "..." } }, { visibilidad: { $eq: "PUBLICO" } } ] } } },
     *   { $match: { embeddingModelo: "gemini-embedding-001/768" } },
     *   { $limit: 10 },
     *   { $project: { ..., puntaje: { $meta: "vectorSearchScore" } } }
     * ])
     * </pre>
     * El {@code $match} descarta los vectores sintéticos de la semilla mientras la tarea programada los
     * regenera: no representan el texto. Por eso {@code $vectorSearch} pide más resultados que el límite.
     */
    private List<ArticuloEncontradoResponse> buscarVector(List<Double> vector, ModuloErp modulo, int limite,
            UsuarioAutenticado actor) {
        List<Document> condiciones = new ArrayList<>();
        if (modulo != null) {
            condiciones.add(new Document("modulo", new Document("$eq", modulo.name())));
        }
        if (!actor.tiene(Permiso.ARTICULOS_VER_INTERNOS)) {
            condiciones.add(new Document("visibilidad", new Document("$eq", Visibilidad.PUBLICO.name())));
        }
        int candidatos = limite * 3;
        Document etapa = new Document("index", INDICE_VECTORIAL)
                .append("path", "embedding")
                .append("queryVector", vector)
                .append("numCandidates", Math.max(100, candidatos * 10))
                .append("limit", candidatos);
        if (condiciones.size() == 1) {
            etapa.append("filter", condiciones.get(0));
        } else if (condiciones.size() > 1) {
            etapa.append("filter", new Document("$and", condiciones));
        }
        List<Bson> pipeline = List.of(
                new Document("$vectorSearch", etapa),
                new Document("$match", new Document("embeddingModelo", embeddings.modelo())),
                new Document("$limit", limite),
                proyeccion("vectorSearchScore"));
        return ejecutar(pipeline);
    }

    /** El vector de la consulta, de la caché o de Gemini. La clave ignora mayúsculas y espacios repetidos. */
    private List<Double> vectorDe(String consulta) {
        String clave = consulta.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<Double> guardado = vectoresConsulta.getIfPresent(clave);
        if (guardado != null) {
            return guardado;
        }
        List<Double> vector = embeddings.consulta(consulta);
        vectoresConsulta.put(clave, vector);
        return vector;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static Document proyeccion(String meta) {
        return new Document("$project", new Document("titulo", 1)
                .append("modulo", 1)
                .append("etiquetas", 1)
                .append("visibilidad", 1)
                .append("extracto", new Document("$substrCP", List.of("$contenido", 0, LARGO_EXTRACTO)))
                .append("puntaje", new Document("$meta", meta)));
    }

    private List<ArticuloEncontradoResponse> ejecutar(List<Bson> pipeline) {
        List<ArticuloEncontradoResponse> resultados = new ArrayList<>();
        for (Document d : template.getCollection(template.getCollectionName(Articulo.class)).aggregate(pipeline)) {
            resultados.add(new ArticuloEncontradoResponse(d.getObjectId("_id").toHexString(), d.getString("titulo"),
                    d.getString("modulo"), d.getList("etiquetas", String.class), d.getString("visibilidad"),
                    d.getString("extracto"), ((Number) d.get("puntaje")).doubleValue()));
        }
        return resultados;
    }

    private static String requerido(String parametro, String valor, int maximo) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty()) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO, "Falta el parámetro '" + parametro + "'.");
        }
        if (limpio.length() > maximo) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                    "El parámetro '" + parametro + "' admite como máximo " + maximo + " caracteres.");
        }
        return limpio;
    }

    /** Vacío: {@value #LIMITE_POR_DEFECTO}. Mayor que {@value #LIMITE_MAXIMO}: se recorta sin error, como la paginación. */
    private static int limite(Integer limite) {
        if (limite == null) {
            return LIMITE_POR_DEFECTO;
        }
        if (limite < 1) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO, "El parámetro 'limite' debe ser 1 o más.");
        }
        return Math.min(limite, LIMITE_MAXIMO);
    }
}
