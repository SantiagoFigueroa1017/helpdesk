package co.edu.ufpso.helpdesk.tickets.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

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

import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.tickets.domain.ComentarioTicket;

/** Guarda y lee comentarios contra el validador real de {@code comentariosTicket}. */
@SpringBootTest
@ActiveProfiles("test")
class ComentarioTicketPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private ComentarioTicketRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "comentariosTicket");
    }

    @Test
    void guardaUnComentarioNuevoSinEditar() {
        ComentarioTicket guardado = repositorio.save(comentario("Ya reinicié el datáfono y sigue sin conectar."));

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class").containsEntry("editado", false);

        ComentarioTicket leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getTexto()).isEqualTo("Ya reinicié el datáfono y sigue sin conectar.");
        assertThat(leido.getEditado()).isFalse();
    }

    @Test
    void elValidadorRechazaUnTextoSoloConEspacios() {
        ComentarioTicket comentario = comentario("   ");
        assertThatThrownBy(() -> repositorio.save(comentario)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ComentarioTicket comentario(String texto) {
        ComentarioTicket c = new ComentarioTicket();
        c.setFechaCreacion(AHORA);
        c.setFechaActualizacion(AHORA);
        c.setTicketId(new ObjectId());
        c.setAutorId(new ObjectId());
        c.setTexto(texto);
        return c;
    }
}
