package co.edu.ufpso.helpdesk.tickets.controller;

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
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.dto.ComentarioRequest;
import co.edu.ufpso.helpdesk.tickets.dto.ComentarioResponse;
import co.edu.ufpso.helpdesk.tickets.service.ComentarioService;
import jakarta.validation.Valid;

/**
 * Comentarios de ticket (fuera de las 40). La conversación se lee y se crea desde el ticket; un
 * comentario se edita y se borra por su propio id. Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}.
 */
@RestController
public class ComentarioController {

    private final ComentarioService servicio;

    public ComentarioController(ComentarioService servicio) {
        this.servicio = servicio;
    }

    /** {@code ?pagina=0&tamano=20}. De la más antigua a la más reciente; {@code orden=fechaCreacion,desc} invierte. */
    @GetMapping("/api/tickets/{ticketId}/comentarios")
    @PreAuthorize("hasAnyAuthority('TICKETS_VER','TICKETS_VER_PROPIOS')")
    public Pagina<ComentarioResponse> listar(@PathVariable String ticketId, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(Ids.deRuta(ticketId), pageable, actor);
    }

    /** Responde 201 con la cabecera {@code Location}. */
    @PostMapping("/api/tickets/{ticketId}/comentarios")
    @PreAuthorize("hasAuthority('COMENTARIOS_CREAR')")
    public ResponseEntity<ComentarioResponse> crear(@PathVariable String ticketId,
            @Valid @RequestBody ComentarioRequest solicitud, @AuthenticationPrincipal UsuarioAutenticado actor) {
        ComentarioResponse creado = servicio.crear(Ids.deRuta(ticketId), solicitud, actor);
        return ResponseEntity.created(URI.create("/api/comentarios/" + creado.id())).body(creado);
    }

    /** Solo el autor. */
    @PutMapping("/api/comentarios/{id}")
    @PreAuthorize("hasAuthority('COMENTARIOS_CREAR')")
    public ComentarioResponse actualizar(@PathVariable String id, @Valid @RequestBody ComentarioRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizar(Ids.deRuta(id), solicitud, actor);
    }

    /** El autor, o quien tiene {@code COMENTARIOS_ELIMINAR}. Responde 204. */
    @DeleteMapping("/api/comentarios/{id}")
    @PreAuthorize("hasAnyAuthority('COMENTARIOS_CREAR','COMENTARIOS_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        servicio.eliminar(Ids.deRuta(id), actor);
        return ResponseEntity.noContent().build();
    }
}
