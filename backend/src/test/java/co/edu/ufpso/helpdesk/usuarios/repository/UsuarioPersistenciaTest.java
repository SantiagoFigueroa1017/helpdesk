package co.edu.ufpso.helpdesk.usuarios.repository;

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

import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/** Guarda y lee usuarios contra el validador real de {@code usuarios}. */
@SpringBootTest
@ActiveProfiles("test")
class UsuarioPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    /** Hash BCrypt de ejemplo con el formato que exige el validador (60 caracteres). */
    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private UsuarioRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "usuarios");
    }

    @Test
    void guardaUnUsuarioGlobalConEmpresaIdNullExplicito() {
        Usuario agente = usuario("Laura Sanjuán", "laura.sanjuan@sistemasintegrados.com.co", null);
        Usuario guardado = repositorio.save(agente);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class").containsEntry("empresaId", null);

        Usuario leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getEmpresaId()).isNull();
        assertThat(leido.getRolIds()).isEqualTo(agente.getRolIds());
    }

    @Test
    void guardaUnUsuarioDeEmpresaConElEmailEnMinusculas() {
        ObjectId empresa = new ObjectId();
        Usuario cliente = usuario("Camilo Quintero", "  Camilo.Quintero@LacteosElZulia.co ", empresa);
        Usuario guardado = repositorio.save(cliente);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo.getString("email")).isEqualTo("camilo.quintero@lacteoselzulia.co");
        assertThat(crudo.getObjectId("empresaId")).isEqualTo(empresa);
    }

    @Test
    void elValidadorRechazaUnaContrasenaSinBcrypt() {
        Usuario usuario = usuario("Diego Pacheco", "diego.pacheco@lacteoselzulia.co", new ObjectId());
        usuario.setPasswordHash("Helpdesk2026*");

        assertThatThrownBy(() -> repositorio.save(usuario)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elValidadorRechazaUnUsuarioSinRoles() {
        Usuario usuario = usuario("Yeimy Carrascal", "yeimy.carrascal@lacteoselzulia.co", new ObjectId());
        usuario.setRolIds(List.of());

        assertThatThrownBy(() -> repositorio.save(usuario)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Usuario usuario(String nombre, String email, ObjectId empresaId) {
        Usuario u = new Usuario();
        u.setFechaCreacion(AHORA);
        u.setFechaActualizacion(AHORA);
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(HASH);
        u.setRolIds(List.of(new ObjectId(), new ObjectId()));
        u.setEmpresaId(empresaId);
        u.setActivo(true);
        return u;
    }
}
