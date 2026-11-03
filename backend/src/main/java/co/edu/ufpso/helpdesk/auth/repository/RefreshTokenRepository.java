package co.edu.ufpso.helpdesk.auth.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.auth.domain.RefreshToken;

/**
 * Acceso a {@code refreshTokens}. Solo lo usa el servicio de autenticación. Las consultas
 * (por hash para rotar, borrado por usuario para logout y HU08) se agregan en 2.4, apoyadas en
 * {@code ux_refresh_tokenHash} y en el índice por {@code usuarioId}.
 */
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, ObjectId> {
}
