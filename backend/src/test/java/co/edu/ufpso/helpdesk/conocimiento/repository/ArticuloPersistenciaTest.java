package co.edu.ufpso.helpdesk.conocimiento.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee artículos contra el validador real de {@code articulos}. */
@SpringBootTest
@ActiveProfiles("test")
class ArticuloPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");
    private static final String MODELO = "gemini-embedding-001/768";

    @Autowired
    private ArticuloRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "articulos");
    }

    @Test
    void guardaUnArticuloConElEmbeddingPendiente() {
        Articulo articulo = articulo();
        articulo.marcarEmbeddingPendiente();
        Articulo guardado = repositorio.save(articulo);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class")
                .containsEntry("embedding", null)
                .containsEntry("embeddingModelo", null)
                .containsEntry("visibilidad", "INTERNO");

        Articulo leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.embeddingPendiente()).isTrue();
        assertThat(leido.embeddingCoherente()).isTrue();
        assertThat(leido.getTicketIds()).isEqualTo(articulo.getTicketIds());
    }

    @Test
    void guardaLosSetecientosSesentaYOchoValoresComoDouble() {
        Articulo articulo = articulo();
        // Incluye 0.0 y 1.0: desde mongosh se guardarían como int y el validador los rechazaría.
        List<Double> vector = vector(Articulo.DIMENSIONES);
        vector.set(0, 0.0);
        vector.set(1, 1.0);
        articulo.asignarEmbedding(vector, MODELO);
        Articulo guardado = repositorio.save(articulo);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        List<?> embeddingCrudo = crudo.getList("embedding", Object.class);
        assertThat(embeddingCrudo).hasSize(768).allMatch(v -> v instanceof Double);

        Articulo leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getEmbedding()).isEqualTo(vector);
        assertThat(leido.getEmbeddingModelo()).isEqualTo(MODELO);
        assertThat(leido.embeddingCoherente()).isTrue();
    }

    @Test
    void elValidadorRechazaUnVectorDeSetecientosSesentaYSiete() {
        Articulo articulo = articulo();
        articulo.asignarEmbedding(vector(767), MODELO);

        assertThat(articulo.embeddingCoherente()).isFalse();
        assertThatThrownBy(() -> repositorio.save(articulo)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elValidadorRechazaUnVectorSinModelo() {
        Articulo articulo = articulo();
        articulo.asignarEmbedding(vector(Articulo.DIMENSIONES), null);

        assertThat(articulo.embeddingCoherente()).isFalse();
        assertThatThrownBy(() -> repositorio.save(articulo)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Articulo articulo() {
        Articulo a = new Articulo();
        a.setFechaCreacion(AHORA);
        a.setFechaActualizacion(AHORA);
        a.setTitulo("El datáfono no conecta después de cambiar la red WiFi de la caja");
        a.setContenido("Verifique que el datáfono tenga la nueva clave de la red y reinicie el turno de caja.");
        a.setModulo(ModuloErp.VENTAS_EN_SITIO);
        a.setEtiquetas(List.of("datafono", "wifi", "caja"));
        a.setTicketIds(List.of(new ObjectId(), new ObjectId()));
        a.setAutorId(new ObjectId());
        return a;
    }

    private static List<Double> vector(int dimensiones) {
        List<Double> v = new ArrayList<>(dimensiones);
        for (int i = 0; i < dimensiones; i++) {
            v.add(Math.sin(i) / 10);
        }
        return v;
    }
}
