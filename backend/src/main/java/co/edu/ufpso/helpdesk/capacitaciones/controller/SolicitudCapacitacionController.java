package co.edu.ufpso.helpdesk.capacitaciones.controller;

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

import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoSolicitud;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CrearSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.ProgramarSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.RechazarSolicitudRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.SolicitudResponse;
import co.edu.ufpso.helpdesk.capacitaciones.service.SolicitudCapacitacionService;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/**
 * Solicitudes de capacitación personalizada (fuera de las 40; programar es parte de HU33). Permisos
 * según {@code modelo/MATRIZ_PERMISOS_2.4.md}.
 */
@RestController
@RequestMapping("/api/solicitudes-capacitacion")
public class SolicitudCapacitacionController {

    private final SolicitudCapacitacionService servicio;

    public SolicitudCapacitacionController(SolicitudCapacitacionService servicio) {
        this.servicio = servicio;
    }

    /** El CLIENTE_ADMIN la registra para su empresa. Responde 201 con {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('CAPACITACIONES_SOLICITAR')")
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody CrearSolicitudRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        SolicitudResponse creada = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/solicitudes-capacitacion/" + creada.id())).body(creada);
    }

    /** {@code ?estado=PENDIENTE&empresaId=&pagina=0&tamano=20&orden=fechaCreacion,asc}. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CAPACITACIONES_SOLICITAR','SOLICITUDES_GESTIONAR')")
    public Pagina<SolicitudResponse> listar(@RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) String empresaId, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(estado, empresaId, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CAPACITACIONES_SOLICITAR','SOLICITUDES_GESTIONAR')")
    public SolicitudResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU33: crea la capacitación PERSONALIZADA y marca la solicitud en una transacción. Responde la capacitación. */
    @PutMapping("/{id}/programar")
    @PreAuthorize("hasAuthority('SOLICITUDES_GESTIONAR')")
    public CapacitacionResponse programar(@PathVariable String id, @Valid @RequestBody ProgramarSolicitudRequest datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.programar(Ids.deRuta(id), datos, actor);
    }

    /** HU33: rechazar indicando el motivo. */
    @PutMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('SOLICITUDES_GESTIONAR')")
    public SolicitudResponse rechazar(@PathVariable String id, @Valid @RequestBody RechazarSolicitudRequest datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.rechazar(Ids.deRuta(id), datos, actor);
    }

    /** Borrado físico; nunca una PROGRAMADA. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CAPACITACIONES_SOLICITAR','SOLICITUDES_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        servicio.eliminar(Ids.deRuta(id), actor);
        return ResponseEntity.noContent().build();
    }
}
