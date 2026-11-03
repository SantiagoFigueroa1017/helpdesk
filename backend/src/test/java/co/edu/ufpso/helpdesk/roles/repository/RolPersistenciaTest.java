package co.edu.ufpso.helpdesk.roles.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee roles contra el validador real de {@code roles}. */
@SpringBootTest
@ActiveProfiles("test")
class RolPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private RolRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "roles");
    }

    @Test
    void elCatalogoDePermisosCoincideConElDelValidador() {
        Document esquema = ValidadoresDePrueba.validadorDe(mongoClient, "roles").get("$jsonSchema", Document.class);
        Document items = esquema.get("properties", Document.class)
                .get("permisos", Document.class)
                .get("items", Document.class);
        List<String> delValidador = items.getList("enum", String.class);

        assertThat(Arrays.stream(Permiso.values()).map(Enum::name))
                .hasSize(49)
                .containsExactlyInAnyOrderElementsOf(delValidador);
    }

    @Test
    void guardaUnRolConLosCuarentaYNuevePermisos() {
        Rol rol = rol("ADMIN_PRUEBA", List.of(Permiso.values()));
        Rol guardado = repositorio.save(rol);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class");
        assertThat(crudo.getList("permisos", String.class)).contains("ROLES_GESTIONAR").hasSize(49);
        assertThat(crudo.get("alcance")).isEqualTo("GLOBAL");

        Rol leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getPermisos()).containsExactly(Permiso.values());
        assertThat(leido.getAlcance()).isEqualTo(AlcanceRol.GLOBAL);
        assertThat(leido.getEsSistema()).isFalse();
    }

    @Test
    void elValidadorRechazaUnRolSinPermisos() {
        assertThatThrownBy(() -> repositorio.save(rol("SIN_PERMISOS", List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elValidadorRechazaPermisosRepetidos() {
        Rol rol = rol("REPETIDOS", List.of(Permiso.TICKETS_VER, Permiso.TICKETS_VER));
        assertThatThrownBy(() -> repositorio.save(rol)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Rol rol(String nombre, List<Permiso> permisos) {
        Rol rol = new Rol();
        rol.setFechaCreacion(AHORA);
        rol.setFechaActualizacion(AHORA);
        rol.setNombre(nombre);
        rol.setDescripcion("Rol creado por RolPersistenciaTest");
        rol.setAlcance(AlcanceRol.GLOBAL);
        rol.setPermisos(permisos);
        rol.setActivo(true);
        rol.setEsSistema(false);
        return rol;
    }
}
