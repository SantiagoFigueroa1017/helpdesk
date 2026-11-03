package co.edu.ufpso.helpdesk.capacitaciones.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;

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

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee capacitaciones contra el validador real de {@code capacitaciones}. */
@SpringBootTest
@ActiveProfiles("test")
class CapacitacionPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-20T14:00:00Z");

    @Autowired
    private CapacitacionRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "capacitaciones");
    }

    @Test
    void guardaUnaMasivaConEmpresaYSolicitudEnNullYCalculaLaFechaFin() {
        Capacitacion masiva = capacitacion(TipoCapacitacion.MASIVA, null);
        Capacitacion guardada = repositorio.save(masiva);

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).doesNotContainKey("_class")
                .containsEntry("empresaId", null)
                .containsEntry("solicitudId", null)
                .containsEntry("cuposOcupados", 0)
                .containsEntry("estado", "PROGRAMADA");
        assertThat(crudo.get("fechaFin")).isEqualTo(Date.from(INICIO.plusSeconds(90 * 60)));

        Capacitacion leida = repositorio.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getFechaFin()).isEqualTo(INICIO.plusSeconds(90 * 60));
        assertThat(leida.cuposDisponibles()).isEqualTo(30);
        assertThat(leida.tipoCoherente()).isTrue();
    }

    @Test
    void guardaUnaPersonalizadaSinSolicitudPrevia() {
        Capacitacion personalizada = capacitacion(TipoCapacitacion.PERSONALIZADA, new ObjectId());
        Capacitacion guardada = repositorio.save(personalizada);

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo.getObjectId("empresaId")).isEqualTo(personalizada.getEmpresaId());
        assertThat(crudo).containsEntry("solicitudId", null);
    }

    @Test
    void elValidadorRechazaUnaMasivaConEmpresa() {
        Capacitacion masiva = capacitacion(TipoCapacitacion.MASIVA, new ObjectId());

        assertThat(masiva.tipoCoherente()).isFalse();
        assertThatThrownBy(() -> repositorio.save(masiva)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void realizadaSeDerivaDeLaFechaFin() {
        Capacitacion c = capacitacion(TipoCapacitacion.MASIVA, null);

        assertThat(c.realizada(INICIO.plusSeconds(60))).isFalse();
        assertThat(c.realizada(INICIO.plusSeconds(91 * 60))).isTrue();
    }

    private static Capacitacion capacitacion(TipoCapacitacion tipo, ObjectId empresaId) {
        Capacitacion c = new Capacitacion();
        c.setFechaCreacion(AHORA);
        c.setFechaActualizacion(AHORA);
        c.setTitulo("Cierre de caja y arqueo en el POS");
        c.setTipo(tipo);
        c.setModulo(ModuloErp.VENTAS_EN_SITIO);
        c.setEmpresaId(empresaId);
        c.setFechaInicio(INICIO);
        c.setDuracionMinutos(90);
        c.setCupo(30);
        c.setInstructorId(new ObjectId());
        return c;
    }
}
