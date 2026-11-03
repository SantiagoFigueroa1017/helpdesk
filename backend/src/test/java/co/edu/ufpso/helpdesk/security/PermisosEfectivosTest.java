package co.edu.ufpso.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.roles.service.RolSesion;
import co.edu.ufpso.helpdesk.roles.service.RolSesionService;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesion;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesionService;

/** Reglas de los permisos efectivos (contexto 8.4). Sin Spring ni MongoDB. */
class PermisosEfectivosTest {

    private final UsuarioSesionService usuarios = mock(UsuarioSesionService.class);
    private final RolSesionService roles = mock(RolSesionService.class);
    private final PermisosEfectivos permisos = new PermisosEfectivos(usuarios, roles);

    private final ObjectId empresa = new ObjectId();

    private RolSesion rol(AlcanceRol alcance, boolean activo, Permiso... lista) {
        RolSesion r = new RolSesion(new ObjectId(), "rol", alcance, activo, Set.of(lista));
        when(roles.buscarParaSesion(r.id())).thenReturn(Optional.of(r));
        return r;
    }

    private UsuarioSesion usuario(boolean activo, RolSesion... lista) {
        List<ObjectId> ids = java.util.Arrays.stream(lista).map(RolSesion::id).toList();
        return new UsuarioSesion(new ObjectId(), "Diego Pacheco", "diego.pacheco@empresa.co", empresa, ids, activo);
    }

    @Test
    void losPermisosSonLaUnionDeLosRolesActivos() {
        RolSesion cliente = rol(AlcanceRol.EMPRESA, true, Permiso.TICKETS_CREAR, Permiso.TICKETS_VER_PROPIOS);
        RolSesion admin = rol(AlcanceRol.EMPRESA, true, Permiso.TICKETS_VER, Permiso.TICKETS_CREAR);

        UsuarioAutenticado u = permisos.resolver(usuario(true, cliente, admin)).orElseThrow();

        assertThat(u.permisos()).containsExactlyInAnyOrder(
                Permiso.TICKETS_CREAR, Permiso.TICKETS_VER_PROPIOS, Permiso.TICKETS_VER);
        assertThat(u.alcance()).isEqualTo(AlcanceRol.EMPRESA);
        assertThat(u.esDeEmpresa()).isTrue();
        assertThat(u.empresaId()).isEqualTo(empresa);
    }

    @Test
    void unRolInactivoNoAportaPermisos() {
        RolSesion activo = rol(AlcanceRol.EMPRESA, true, Permiso.TICKETS_CREAR);
        RolSesion inactivo = rol(AlcanceRol.EMPRESA, false, Permiso.TICKETS_VER);

        UsuarioAutenticado u = permisos.resolver(usuario(true, activo, inactivo)).orElseThrow();

        assertThat(u.permisos()).containsExactly(Permiso.TICKETS_CREAR);
    }

    @Test
    void sinRolesActivosNoHaySesion() {
        RolSesion inactivo = rol(AlcanceRol.EMPRESA, false, Permiso.TICKETS_CREAR);
        assertThat(permisos.resolver(usuario(true, inactivo))).isEmpty();
    }

    @Test
    void unRolBorradoSeIgnora() {
        RolSesion activo = rol(AlcanceRol.EMPRESA, true, Permiso.TICKETS_CREAR);
        ObjectId borrado = new ObjectId();
        when(roles.buscarParaSesion(borrado)).thenReturn(Optional.empty());
        UsuarioSesion u = new UsuarioSesion(new ObjectId(), "Laura Sanjuán", "laura@empresa.co", empresa,
                List.of(borrado, activo.id()), true);

        assertThat(permisos.resolver(u).orElseThrow().permisos()).containsExactly(Permiso.TICKETS_CREAR);
    }

    @Test
    void unUsuarioInactivoNoTieneSesionAunqueSusRolesEstenActivos() {
        RolSesion activo = rol(AlcanceRol.EMPRESA, true, Permiso.TICKETS_CREAR);
        assertThat(permisos.resolver(usuario(false, activo))).isEmpty();
    }

    @Test
    void rolesActivosDeAlcancesDistintosNieganElAcceso() {
        RolSesion global = rol(AlcanceRol.GLOBAL, true, Permiso.ROLES_GESTIONAR);
        RolSesion deEmpresa = rol(AlcanceRol.EMPRESA, true, Permiso.TICKETS_CREAR);
        assertThat(permisos.resolver(usuario(true, global, deEmpresa))).isEmpty();
    }

    @Test
    void porIdBuscaElUsuarioYUnUsuarioQueNoExisteNoTieneSesion() {
        ObjectId id = new ObjectId();
        when(usuarios.buscarParaSesion(id)).thenReturn(Optional.empty());
        assertThat(permisos.resolver(id)).isEmpty();
    }
}
