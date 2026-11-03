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

import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoSolicitud;
import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee solicitudes contra el validador real de {@code solicitudesCapacitacion}. */
@SpringBootTest
@ActiveProfiles("test")
class SolicitudCapacitacionPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private SolicitudCapacitacionRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "solicitudesCapacitacion");
    }

    @Test
    void guardaUnaPendienteSinFechaPreferida() {
        SolicitudCapacitacion guardada = repositorio.save(solicitud());

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).doesNotContainKey("_class")
                .doesNotContainKey("fechaPreferida")
                .containsEntry("estado", "PENDIENTE")
                .containsEntry("capacitacionId", null)
                .containsEntry("motivoRechazo", null)
                .containsEntry("atendidaPorId", null);
    }

    @Test
    void guardaUnaProgramada() {
        SolicitudCapacitacion solicitud = solicitud();
        solicitud.setFechaPreferida(Instant.parse("2026-11-25T13:00:00Z"));
        solicitud.programar(new ObjectId(), new ObjectId());
        SolicitudCapacitacion guardada = repositorio.save(solicitud);

        SolicitudCapacitacion leida = repositorio.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getEstado()).isEqualTo(EstadoSolicitud.PROGRAMADA);
        assertThat(leida.getCapacitacionId()).isEqualTo(solicitud.getCapacitacionId());
        assertThat(leida.getFechaPreferida()).isEqualTo(solicitud.getFechaPreferida());
        assertThat(leida.estadoCoherente()).isTrue();
    }

    @Test
    void guardaUnaRechazadaConSuMotivo() {
        SolicitudCapacitacion solicitud = solicitud();
        solicitud.rechazar("El plan no incluye más capacitaciones en esta vigencia.", new ObjectId());

        SolicitudCapacitacion guardada = repositorio.save(solicitud);

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).containsEntry("estado", "RECHAZADA").containsEntry("capacitacionId", null);
    }

    @Test
    void vuelveAPendienteConservandoQuienLaAtendio() {
        ObjectId agente = new ObjectId();
        SolicitudCapacitacion solicitud = solicitud();
        solicitud.programar(new ObjectId(), agente);
        solicitud.devolverAPendiente();

        SolicitudCapacitacion guardada = repositorio.save(solicitud);

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).containsEntry("estado", "PENDIENTE")
                .containsEntry("capacitacionId", null)
                .containsEntry("atendidaPorId", agente);
    }

    @Test
    void elValidadorRechazaUnaProgramadaSinCapacitacion() {
        SolicitudCapacitacion solicitud = solicitud();
        solicitud.programar(new ObjectId(), new ObjectId());
        // Estado imposible desde los métodos del documento; se fuerza para probar el validador.
        ReflectionTestUtils.setField(solicitud, "capacitacionId", null);

        assertThat(solicitud.estadoCoherente()).isFalse();
        assertThatThrownBy(() -> repositorio.save(solicitud)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static SolicitudCapacitacion solicitud() {
        SolicitudCapacitacion s = new SolicitudCapacitacion();
        s.setFechaCreacion(AHORA);
        s.setFechaActualizacion(AHORA);
        s.setEmpresaId(new ObjectId());
        s.setSolicitanteId(new ObjectId());
        s.setModulo(ModuloErp.INVENTARIOS);
        s.setDescripcion("El equipo de bodega necesita repasar los ajustes de inventario y los traslados.");
        s.setParticipantesEstimados(8);
        return s;
    }
}
