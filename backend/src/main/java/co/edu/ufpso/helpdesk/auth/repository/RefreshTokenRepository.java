package co.edu.ufpso.helpdesk.auth.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.auth.domain.RefreshToken;

/**
 * Acceso a {@code refreshTokens}. Solo lo usa el servicio de autenticación. La rotación toma y
 * borra el documento en una sola operación con {@code MongoTemplate.findAndRemove} (ver
 * {@code auth.service.AuthService}). El borrado por usuario (HU08) se agrega en 2.5, apoyado en
 * el índice por {@code usuarioId}.
 */
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, ObjectId> {

    /** Logout. Usa {@code ux_refresh_tokenHash}. Devuelve cuántos documentos borró (0 o 1). */
    long deleteByTokenHash(String tokenHash);
}
