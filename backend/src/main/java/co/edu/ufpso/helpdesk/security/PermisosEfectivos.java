package co.edu.ufpso.helpdesk.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.roles.service.RolSesion;
import co.edu.ufpso.helpdesk.roles.service.RolSesionService;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesion;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesionService;

/**
 * Arma el {@link UsuarioAutenticado} a partir de su id (contexto 8.4, acta 2.4). Se llama en cada
 * petición, en el login y en cada renovación; las lecturas pasan por las cachés de
 * {@link UsuarioSesionService} y {@link RolSesionService}.
 *
 * <p>Devuelve vacío, y el usuario no puede usar el sistema, cuando:
 * <ul>
 *   <li>el usuario no existe o está inactivo;</li>
 *   <li>ninguno de sus roles existe y está activo (contexto 8.4: sin roles activos no hay sesión);</li>
 *   <li>sus roles activos mezclan alcances. Las reglas de 8.4 lo impiden al asignar roles; si
 *       aparece en los datos, se niega el acceso antes que adivinar qué filtro aplicar.</li>
 * </ul>
 */
@Service
public class PermisosEfectivos {

    private static final Logger log = LoggerFactory.getLogger(PermisosEfectivos.class);

    private final UsuarioSesionService usuarios;
    private final RolSesionService roles;

    public PermisosEfectivos(UsuarioSesionService usuarios, RolSesionService roles) {
        this.usuarios = usuarios;
        this.roles = roles;
    }

    public Optional<UsuarioAutenticado> resolver(ObjectId usuarioId) {
        return usuarios.buscarParaSesion(usuarioId).flatMap(this::resolver);
    }

    public Optional<UsuarioAutenticado> resolver(UsuarioSesion usuario) {
        if (!usuario.activo()) {
            return Optional.empty();
        }
        List<RolSesion> activos = usuario.rolIds().stream()
                .map(roles::buscarParaSesion)
                .flatMap(Optional::stream)
                .filter(RolSesion::activo)
                .toList();
        if (activos.isEmpty()) {
            return Optional.empty();
        }
        Set<AlcanceRol> alcances = EnumSet.noneOf(AlcanceRol.class);
        Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
        for (RolSesion rol : activos) {
            alcances.add(rol.alcance());
            permisos.addAll(rol.permisos());
        }
        if (alcances.size() > 1) {
            log.warn("El usuario {} tiene roles activos de alcances distintos; se niega el acceso", usuario.id());
            return Optional.empty();
        }
        return Optional.of(new UsuarioAutenticado(usuario.id(), usuario.nombre(), usuario.email(),
                usuario.empresaId(), alcances.iterator().next(), permisos));
    }
}
