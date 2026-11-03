package co.edu.ufpso.helpdesk.conocimiento.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;

/**
 * Genera los vectores que faltan (modelo 1.2, "Embeddings"; tarea 2.7). Cada 10 minutos toma hasta
 * {@code helpdesk.gemini.lote-regeneracion} artículos cuyo {@code embeddingModelo} no es el vigente: los que
 * quedaron con el vector pendiente porque Gemini falló, los 20 de la semilla sin vector y los 20 con vectores
 * sintéticos ({@code sintetico/768}). Con 40 artículos y lotes de 10, la semilla queda completa en unos
 * 40 minutos sin pasar de la cuota del nivel gratuito.
 *
 * <p>Escribe el vector solo si el artículo no cambió mientras Gemini respondía ({@code fechaActualizacion}
 * igual a la leída). Si alguien lo editó en medio, esa edición ya generó su propio vector o lo dejó pendiente
 * para la siguiente corrida. No cambia {@code fechaActualizacion}: el contenido sigue igual.
 *
 * <p>Si Gemini responde 429, la corrida se detiene y la siguiente sigue donde quedó.
 *
 * <p>Se apaga con {@code helpdesk.gemini.regeneracion-activa: false} (perfil de pruebas).
 */
@Component
@ConditionalOnProperty(name = "helpdesk.gemini.regeneracion-activa", havingValue = "true", matchIfMissing = true)
public class RegeneracionEmbeddings {

    private static final Logger LOG = LoggerFactory.getLogger(RegeneracionEmbeddings.class);

    private final MongoTemplate template;
    private final ProveedorEmbeddings embeddings;
    private final PropiedadesGemini propiedades;

    public RegeneracionEmbeddings(MongoTemplate template, ProveedorEmbeddings embeddings,
            PropiedadesGemini propiedades) {
        this.template = template;
        this.embeddings = embeddings;
        this.propiedades = propiedades;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${helpdesk.gemini.intervalo-regeneracion:PT10M}")
    public void corrida() {
        if (!embeddings.disponible()) {
            return;
        }
        int generados = regenerar(propiedades.loteRegeneracion());
        if (generados > 0) {
            LOG.info("Vectores generados en esta corrida: {}.", generados);
        }
    }

    /**
     * Procesa hasta {@code lote} artículos pendientes. Público para las pruebas.
     *
     * @return cuántos artículos quedaron con vector
     */
    public int regenerar(int lote) {
        Query pendientes = new Query(Criteria.where("embeddingModelo").ne(embeddings.modelo())).limit(lote);
        pendientes.fields().include("titulo", "etiquetas", "contenido", "fechaActualizacion");
        List<Articulo> articulos = template.find(pendientes, Articulo.class);

        int generados = 0;
        for (Articulo a : articulos) {
            List<Double> vector;
            try {
                vector = embeddings.documento(TextoEmbedding.de(a.getTitulo(), a.getEtiquetas(), a.getContenido()));
            } catch (EmbeddingNoDisponibleException e) {
                if (e.getMotivo() == EmbeddingNoDisponibleException.Motivo.ERROR) {
                    continue;
                }
                // Sin cuota o sin clave: no vale la pena seguir con el resto del lote.
                break;
            }
            Query mismaVersion = new Query(Criteria.where("_id").is(a.getId())
                    .and("fechaActualizacion").is(a.getFechaActualizacion()));
            Update asignar = new Update().set("embedding", vector).set("embeddingModelo", embeddings.modelo());
            if (template.updateFirst(mismaVersion, asignar, Articulo.class).getModifiedCount() == 1) {
                generados++;
            }
        }
        return generados;
    }
}
