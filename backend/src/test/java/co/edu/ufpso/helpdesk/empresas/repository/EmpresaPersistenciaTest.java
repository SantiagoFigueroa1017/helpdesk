package co.edu.ufpso.helpdesk.empresas.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.empresas.domain.Contacto;
import co.edu.ufpso.helpdesk.empresas.domain.Empresa;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee empresas contra el validador real de {@code empresas}. */
@SpringBootTest
@ActiveProfiles("test")
class EmpresaPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private EmpresaRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "empresas");
    }

    @Test
    void guardaUnaEmpresaConElContactoCompleto() {
        Contacto contacto = new Contacto("Natalia Ascanio", "compras@lacteoselzulia.co", "+57 (607) 562 1180",
                "Calle 11 # 14-32", "Ocaña");
        Empresa guardada = repositorio.save(empresa("900123456-1", contacto));

        Document crudo = coleccion.find(eq("_id", guardada.getId())).first();
        assertThat(crudo).doesNotContainKey("_class");
        assertThat(crudo.get("contacto", Document.class)).doesNotContainKey("_class");

        Empresa leida = repositorio.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getContacto()).isEqualTo(contacto);
    }

    @Test
    void omiteDireccionYCiudadCuandoNoVienen() {
        Contacto contacto = new Contacto("Andrés Ropero", "andres.ropero@ferreteriaropero.co", "3157781020",
                null, null);
        Empresa guardada = repositorio.save(empresa("890505123-7", contacto));

        Document contactoCrudo = coleccion.find(eq("_id", guardada.getId())).first().get("contacto", Document.class);
        assertThat(contactoCrudo).containsOnlyKeys("nombre", "email", "telefono");

        Empresa leida = repositorio.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getContacto().direccion()).isNull();
        assertThat(leida.getContacto().ciudad()).isNull();
    }

    @Test
    void elValidadorRechazaUnNitSinDigitoDeVerificacion() {
        Empresa empresa = empresa("900123456",
                new Contacto("Natalia Ascanio", "compras@lacteoselzulia.co", "3157781020", null, null));

        assertThatThrownBy(() -> repositorio.save(empresa)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Empresa empresa(String nit, Contacto contacto) {
        Empresa e = new Empresa();
        e.setFechaCreacion(AHORA);
        e.setFechaActualizacion(AHORA);
        e.setNit(nit);
        e.setRazonSocial("Lácteos El Zulia S.A.S.");
        e.setContacto(contacto);
        return e;
    }
}
