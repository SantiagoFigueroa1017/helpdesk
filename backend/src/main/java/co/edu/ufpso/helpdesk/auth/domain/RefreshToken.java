package co.edu.ufpso.helpdesk.auth.domain;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección técnica {@code refreshTokens}. Modelo 1.2, sección 13.
 *
 * <p>Una sesión activa. Se guarda el hash del token, nunca el token (contexto 8.3). Desde 2.4,
 * {@code tokenHash} es el SHA-256 del refresh token en hexadecimal minúscula (64 caracteres), y
 * el validador rechaza cualquier otro formato. El token que recibe el cliente son 32 bytes de
 * {@code SecureRandom} en Base64URL, con vida de 7 días. El índice TTL
 * {@code ttl_refresh_expiraEn} borra el documento cuando pasa {@code expiraEn}.
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
