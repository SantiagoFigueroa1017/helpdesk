package co.edu.ufpso.helpdesk.roles.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.roles.domain.Rol;

/**
 * Acceso a {@code roles}. Solo lo usa el servicio de roles (regla entre módulos de 2.2).
 * Las consultas (por nombre, roles activos de un usuario) se agregan en 2.4, apoyadas en
 * {@code ux_roles_nombre}.
 */
public interface RolRepository extends MongoRepository<Rol, ObjectId> {
}
