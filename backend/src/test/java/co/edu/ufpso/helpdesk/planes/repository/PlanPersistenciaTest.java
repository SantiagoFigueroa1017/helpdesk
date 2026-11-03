package co.edu.ufpso.helpdesk.planes.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.planes.domain.LimitesPlan;
import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import co.edu.ufpso.helpdesk.planes.domain.Plan;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee planes contra el validador real de {@code planes}. */
@SpringBootTest
@ActiveProfiles("test")
class PlanPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private PlanRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "planes");
    }

    @Test
    void guardaElPrecioComoDecimal128YLosLimitesComoInt32() {
        LimitesPlan limites = new LimitesPlan(24, 2, 25, 3);
        Plan guardado = repositorio.save(plan(new BigDecimal("450000.00"), limites));

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class");
        assertThat(crudo.get("precio")).isEqualTo(new Decimal128(new BigDecimal("450000.00")));
        Document limitesCrudos = crudo.get("limites", Document.class);
        assertThat(limitesCrudos.get("horasRespuesta")).isInstanceOf(Integer.class);
        assertThat(limitesCrudos.get("maxModulos")).isInstanceOf(Integer.class);

        Plan leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getPrecio()).isEqualTo(new BigDecimal("450000.00"));
        assertThat(leido.getLimites()).isEqualTo(limites);
        assertThat(leido.getNivelAcceso()).isEqualTo(NivelAcceso.ESTANDAR);
    }

    @Test
    void elValidadorRechazaUnPrecioCero() {
        Plan plan = plan(BigDecimal.ZERO, new LimitesPlan(24, 2, 25, 3));
        assertThatThrownBy(() -> repositorio.save(plan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elValidadorRechazaMasDeCincoModulos() {
        Plan plan = plan(new BigDecimal("450000.00"), new LimitesPlan(24, 2, 25, 6));
        assertThatThrownBy(() -> repositorio.save(plan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Plan plan(BigDecimal precio, LimitesPlan limites) {
        Plan p = new Plan();
        p.setFechaCreacion(AHORA);
        p.setFechaActualizacion(AHORA);
        p.setNombre("Estándar de prueba");
        p.setDescripcion("Plan creado por PlanPersistenciaTest");
        p.setPrecio(precio);
        p.setNivelAcceso(NivelAcceso.ESTANDAR);
        p.setLimites(limites);
        p.setActivo(true);
        return p;
    }
}
