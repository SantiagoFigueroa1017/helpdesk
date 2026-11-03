package co.edu.ufpso.helpdesk.usuarios.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Acceso a {@code usuarios}. Solo lo usa el servicio de usuarios (regla entre módulos de 2.2).
 * Las consultas (login por email, listado por empresa, conteo por rol) se agregan en 2.4 y 2.5,
 * apoyadas en {@code ux_usuarios_email}, {@code ix_usuarios_empresa_nombre} e
 * {@code ix_usuarios_rolIds}.
 */
public interface UsuarioRepository extends MongoRepository<Usuario, ObjectId> {
}
