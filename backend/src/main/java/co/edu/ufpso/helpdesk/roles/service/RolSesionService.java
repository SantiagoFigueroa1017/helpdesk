package co.edu.ufpso.helpdesk.roles.service;

import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.roles.repository.RolRepository;

/**
 * Lectura de roles para la verificación de cada petición, con la caché {@code rolesSesion}
 * (acta 2.4).
 *
 * <p>HU27 pide que un cambio de permisos aplique de inmediato. El CRUD de roles (HU25 a HU28)
 * llama a {@link #invalidar} al editar, activar, desactivar o eliminar un rol.
 */
@Service
public class RolSesionService {

    public static final String CACHE = "rolesSesion";

    private final RolRepository repositorio;

    public RolSesionService(RolRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Cacheable(cacheNames = CACHE, key = "#id")
    public Optional<RolSesion> buscarParaSesion(ObjectId id) {
        return repositorio.findById(id).map(RolSesionService::aSesion);
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void invalidar(ObjectId id) {
        // Solo vacía la entrada de la caché.
    }

    private static RolSesion aSesion(Rol r) {
        return new RolSesion(r.getId(), r.getNombre(), r.getAlcance(), Boolean.TRUE.equals(r.getActivo()),
                Set.copyOf(r.getPermisos()));
    }
}
