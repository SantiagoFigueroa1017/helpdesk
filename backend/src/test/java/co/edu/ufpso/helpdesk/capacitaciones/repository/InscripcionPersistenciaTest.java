package co.edu.ufpso.helpdesk.capacitaciones.repository;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee inscripciones contra el validador real de {@code inscripciones}. */
@SpringBootTest
@ActiveProfiles("test")
class InscripcionPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private InscripcionRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "inscripciones");
    }

    @Test
    void guardaUnaInscripcionSinMarcarConLosTresCamposEnNull() {
        Inscripcion guardada = repositorio.save(inscripcion());

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).doesNotContainKey("_class")
                .containsEntry("asistio", null)
                .containsEntry("marcadoPorId", null)
                .containsEntry("fechaMarcado", null);
    }

    @Test
    void guardaYLeeLaAsistenciaMarcada() {
        Inscripcion inscripcion = inscripcion();
        ObjectId instructor = new ObjectId();
        inscripcion.marcarAsistencia(true, instructor, AHORA);
        Inscripcion guardada = repositorio.save(inscripcion);

        Inscripcion leida = repositorio.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getAsistio()).isTrue();
        assertThat(leida.getMarcadoPorId()).isEqualTo(instructor);
        assertThat(leida.getFechaMarcado()).isEqualTo(AHORA);
        assertThat(leida.marcaCoherente()).isTrue();
    }

    @Test
    void elValidadorRechazaUnaAsistenciaSinQuienLaMarco() {
        Inscripcion inscripcion = inscripcion();
        inscripcion.marcarAsistencia(true, new ObjectId(), AHORA);
        // Estado imposible desde los métodos del documento; se fuerza para probar el validador.
        ReflectionTestUtils.setField(inscripcion, "marcadoPorId", null);

        assertThat(inscripcion.marcaCoherente()).isFalse();
        assertThatThrownBy(() -> repositorio.save(inscripcion)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Inscripcion inscripcion() {
        Inscripcion i = new Inscripcion();
        i.setFechaCreacion(AHORA);
        i.setFechaActualizacion(AHORA);
        i.setCapacitacionId(new ObjectId());
        i.setUsuarioId(new ObjectId());
        i.setEmpresaId(new ObjectId());
        return i;
    }
}
