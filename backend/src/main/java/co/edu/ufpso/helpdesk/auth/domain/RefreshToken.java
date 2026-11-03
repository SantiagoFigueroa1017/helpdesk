package co.edu.ufpso.helpdesk.auth.domain;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección técnica {@code refreshTokens}. Modelo 1.2, sección 13.
 *
 * <p>Una sesión activa. Se guarda el hash del token, nunca el token (contexto 8.3); el formato
 * del hash y la rotación se fijan en 2.4. El índice TTL {@code ttl_refresh_expiraEn}
 * borra el documento cuando pasa {@code expiraEn}.
 */
@Document("refreshTokens")
public class RefreshToken extends DocumentoBase {

    private ObjectId usuarioId;
    private String tokenHash;
    private Instant expiraEn;

    public RefreshToken() {
    }

    public ObjectId getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(ObjectId usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(Instant expiraEn) {
        this.expiraEn = expiraEn;
    }
}
