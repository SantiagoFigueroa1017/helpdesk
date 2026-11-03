package co.edu.ufpso.helpdesk.membresias.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/**
 * Guarda y lee membresías contra el validador real de {@code membresias}, y comprueba la regla de
 * soporte de la sección 7.5 del contexto.
 */
@SpringBootTest
@ActiveProfiles("test")
class MembresiaPersistenciaTest {

    private static final Instant INICIO = Instant.parse("2026-10-01T05:00:00Z");
    private static final Instant VENCIMIENTO = Instant.parse("2026-11-01T04:59:59Z");

    @Autowired
    private MembresiaRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "membresias");
    }

    @Test
    void guardaYLeeLosModulosContratados() {
        Membresia membresia = membresia(EstadoMembresia.ACTIVA,
                List.of(ModuloErp.VENTAS_EN_SITIO, ModuloErp.INVENTARIOS));
        Membresia guardada = repositorio.save(membresia);

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).doesNotContainKey("_class");
        assertThat(crudo.getList("modulosContratados", String.class))
                .containsExactly("VENTAS_EN_SITIO", "INVENTARIOS");

        Membresia leida = repositorio.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getModulosContratados()).containsExactly(ModuloErp.VENTAS_EN_SITIO, ModuloErp.INVENTARIOS);
        assertThat(leida.getFechaVencimiento()).isEqualTo(VENCIMIENTO);
    }

    @Test
    void elValidadorRechazaUnModuloRepetido() {
        Membresia membresia = membresia(EstadoMembresia.PENDIENTE,
                List.of(ModuloErp.COMPRAS, ModuloErp.COMPRAS));
        assertThatThrownBy(() -> repositorio.save(membresia)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tieneSoporteSoloSiEstaActivaYNoHaVencido() {
        Membresia activa = membresia(EstadoMembresia.ACTIVA, List.of(ModuloErp.COMPRAS));

        assertThat(activa.tieneSoporte(INICIO.plusSeconds(3600))).isTrue();
        // El último instante de la vigencia todavía tiene soporte.
        assertThat(activa.tieneSoporte(VENCIMIENTO)).isTrue();
        assertThat(activa.tieneSoporte(VENCIMIENTO.plusSeconds(1))).isFalse();

        assertThat(membresia(EstadoMembresia.SUSPENDIDA, List.of(ModuloErp.COMPRAS))
                .tieneSoporte(INICIO.plusSeconds(3600))).isFalse();
        assertThat(membresia(EstadoMembresia.PENDIENTE, List.of(ModuloErp.COMPRAS))
                .tieneSoporte(INICIO.plusSeconds(3600))).isFalse();
    }

    private static Membresia membresia(EstadoMembresia estado, List<ModuloErp> modulos) {
        Membresia m = new Membresia();
        m.setFechaCreacion(INICIO);
        m.setFechaActualizacion(INICIO);
        m.setEmpresaId(new ObjectId());
        m.setPlanId(new ObjectId());
        m.setModulosContratados(modulos);
        m.setFechaInicio(INICIO);
        m.setFechaVencimiento(VENCIMIENTO);
        m.setEstado(estado);
        return m;
    }
}
