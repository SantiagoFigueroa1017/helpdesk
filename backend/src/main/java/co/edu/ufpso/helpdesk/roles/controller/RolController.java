package co.edu.ufpso.helpdesk.roles.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.roles.dto.ActualizarRolRequest;
import co.edu.ufpso.helpdesk.roles.dto.CrearRolRequest;
import co.edu.ufpso.helpdesk.roles.dto.PermisoResponse;
import co.edu.ufpso.helpdesk.roles.dto.RolResponse;
import co.edu.ufpso.helpdesk.roles.service.RolService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/**
 * Roles y catálogo de permisos (HU25 a HU28). Consultar exige {@code ROLES_VER}; crear, editar y
 * eliminar exigen {@code ROLES_GESTIONAR}. La anti-escalada la revisa el servicio.
 */
@RestController
@RequestMapping("/api")
public class RolController {

    private final RolService servicio;

    public RolController(RolService servicio) {
        this.servicio = servicio;
    }

    /** HU26: catálogo de los 49 permisos con grupo y descripción. */
    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('ROLES_VER')")
    public List<PermisoResponse> catalogo() {
        return servicio.catalogo();
    }

    /** HU26: {@code ?alcance=EMPRESA&activo=true&pagina=0&tamano=20&orden=nombre,asc}. */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES_VER')")
    public Pagina<RolResponse> listar(@RequestParam(required = false) AlcanceRol alcance,
            @RequestParam(required = false) Boolean activo, Pageable pageable) {
        return servicio.listar(alcance, activo, pageable);
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLES_VER')")
    public RolResponse obtener(@PathVariable String id) {
        return servicio.obtener(Ids.deRuta(id));
    }

    /** HU25. Responde 201 con la cabecera {@code Location}. */
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES_GESTIONAR')")
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody CrearRolRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        RolResponse creado = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/roles/" + creado.id())).body(creado);
    }

    /** HU27. */
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLES_GESTIONAR')")
    public RolResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarRolRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizar(Ids.deRuta(id), solicitud, actor);
    }

    /** HU28. Responde 204. */
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLES_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        servicio.eliminar(Ids.deRuta(id), actor);
        return ResponseEntity.noContent().build();
    }
}
