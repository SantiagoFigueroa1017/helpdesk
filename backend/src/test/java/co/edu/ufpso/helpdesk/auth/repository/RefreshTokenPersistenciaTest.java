package co.edu.ufpso.helpdesk.auth.repository;

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

import co.edu.ufpso.helpdesk.auth.domain.RefreshToken;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/**
 * Guarda y lee sesiones contra el validador real de {@code refreshTokens}. Desde 2.4 el validador
 * exige que {@code tokenHash} sea un SHA-256 en hexadecimal minúscula (64 caracteres).
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private RefreshTokenRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "refreshTokens");
    }

    @Test
    void guardaYLeeUnaSesion() {
        RefreshToken token = new RefreshToken();
        token.setFechaCreacion(AHORA);
        token.setFechaActualizacion(AHORA);
        token.setUsuarioId(new ObjectId());
        token.setTokenHash("9f2c4e7a1b3d5f60718293a4b5c6d7e8f9012a3b4c5d6e7f8091a2b3c4d5e6f7");
        token.setExpiraEn(AHORA.plusSeconds(7 * 24 * 3600));

        RefreshToken guardado = repositorio.save(token);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class");
        assertThat(crudo.get("expiraEn")).isInstanceOf(java.util.Date.class);

        RefreshToken leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getTokenHash()).isEqualTo(token.getTokenHash());
        assertThat(leido.getExpiraEn()).isEqualTo(token.getExpiraEn());
        assertThat(leido.getSchemaVersion()).isEqualTo(1);
    }

    @Test
    void rechazaUnTokenHashQueNoEsSha256EnHexMinuscula() {
        RefreshToken token = new RefreshToken();
        token.setFechaCreacion(AHORA);
        token.setFechaActualizacion(AHORA);
        token.setUsuarioId(new ObjectId());
        token.setTokenHash("9F2C4E7A1B3D5F60718293A4B5C6D7E8F9012A3B4C5D6E7F8091A2B3C4D5E6F7");
        token.setExpiraEn(AHORA.plusSeconds(7 * 24 * 3600));

        assertThatThrownBy(() -> repositorio.save(token)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
