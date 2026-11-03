package co.edu.ufpso.helpdesk.usuarios.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Acceso a {@code usuarios}. Solo lo usa el servicio de usuarios (regla entre módulos de 2.2).
 * Las consultas del listado por empresa se agregan en 2.5, apoyadas en
 * {@code ix_usuarios_empresa_nombre}.
 */
public interface UsuarioRepository extends MongoRepository<Usuario, ObjectId> {

    /** Login (acta 2.4). Usa {@code ux_usuarios_email}; el email llega ya en minúsculas. */
    Optional<Usuario> findByEmail(String email);

    /** Usuarios con el rol entre sus {@code rolIds} (HU27, HU28). Usa {@code ix_usuarios_rolIds}. */
    @Query(value = "{ 'rolIds': ?0 }", count = true)
    long contarConRol(ObjectId rolId);

    /** HU04: usuarios de la empresa. Usa el prefijo de {@code ix_usuarios_empresa_nombre}. */
    long countByEmpresaId(ObjectId empresaId);
}
