package co.edu.ufpso.helpdesk.usuarios.controller;

import java.net.URI;

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

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.dto.ActualizarUsuarioRequest;
import co.edu.ufpso.helpdesk.usuarios.dto.CrearUsuarioRequest;
import co.edu.ufpso.helpdesk.usuarios.dto.UsuarioResponse;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioService;
import jakarta.validation.Valid;

/** Usuarios (HU05 a HU08). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    /** HU06: {@code ?empresaId=&activo=true&texto=cam&pagina=0&tamano=20&orden=nombre,asc}. */
    @GetMapping
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public Pagina<UsuarioResponse> listar(@RequestParam(required = false) String empresaId,
            @RequestParam(required = false) Boolean activo, @RequestParam(required = false) String texto,
            Pageable pageable, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(empresaId, activo, texto, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public UsuarioResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU05. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('USUARIOS_CREAR')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        UsuarioResponse creado = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/usuarios/" + creado.id())).body(creado);
    }

    /** HU07. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public UsuarioResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarUsuarioRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizar(Ids.deRuta(id), solicitud, actor);
    }

    /** HU08. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIOS_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        servicio.eliminar(Ids.deRuta(id), actor);
        return ResponseEntity.noContent().build();
    }
}
