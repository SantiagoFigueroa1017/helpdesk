package co.edu.ufpso.helpdesk.roles.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.roles.domain.Rol;

/**
 * Acceso a {@code roles}. Solo lo usa el módulo de roles (regla entre módulos de 2.2). El listado
 * con filtros opcionales se arma con {@code MongoTemplate} en {@code RolService}.
 */
public interface RolRepository extends MongoRepository<Rol, ObjectId> {

    /** HU25 y HU27: nombre único. Usa {@code ux_roles_nombre}. */
    boolean existsByNombre(String nombre);

    /** HU27: nombre único entre los demás roles. Usa {@code ux_roles_nombre}. */
    boolean existsByNombreAndIdNot(String nombre, ObjectId id);
}
