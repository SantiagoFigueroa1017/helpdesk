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
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.capacitaciones.dto.InscribirRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.InscripcionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.MarcacionAsistenciaResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.MarcarAsistenciaRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.MarcarAsistenciasRequest;
import co.edu.ufpso.helpdesk.capacitaciones.service.InscripcionService;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/** Inscripciones y asistencias (HU37 a HU40). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
public class InscripcionController {

    private final InscripcionService servicio;

    public InscripcionController(InscripcionService servicio) {
        this.servicio = servicio;
    }

    /**
     * HU37. Sin cuerpo (o sin {@code usuarioId}): el usuario se inscribe a sí mismo. Con
     * {@code usuarioId}: el personal de soporte inscribe a ese usuario. Responde 201 con {@code Location}.
     */
    @PostMapping("/api/capacitaciones/{id}/inscripciones")
    @PreAuthorize("hasAnyAuthority('INSCRIPCIONES_PROPIAS','INSCRIPCIONES_GESTIONAR')")
    public ResponseEntity<InscripcionResponse> inscribir(@PathVariable String id,
            @Valid @RequestBody(required = false) InscribirRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        InscripcionResponse creada = servicio.inscribir(Ids.deRuta(id), solicitud, actor);
        return ResponseEntity.created(URI.create("/api/inscripciones/" + creada.id())).body(creada);
    }

    /** HU38: lista de inscritos de una capacitación, con su asistencia. {@code ?pagina=0&tamano=20}. */
    @GetMapping("/api/capacitaciones/{id}/inscripciones")
    @PreAuthorize("hasAuthority('INSCRIPCIONES_VER')")
    public Pagina<InscripcionResponse> inscritos(@PathVariable String id, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listarDeCapacitacion(Ids.deRuta(id), pageable, actor);
    }

    /** HU38: las inscripciones del usuario de la petición. */
    @GetMapping("/api/inscripciones/mias")
    @PreAuthorize("hasAuthority('INSCRIPCIONES_PROPIAS')")
    public Pagina<InscripcionResponse> mias(Pageable pageable, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.mias(pageable, actor);
    }

    /** HU39: marca una inscripción como asistió o no asistió. */
    @PutMapping("/api/inscripciones/{id}/asistencia")
    @PreAuthorize("hasAuthority('ASISTENCIA_MARCAR')")
    public InscripcionResponse marcar(@PathVariable String id, @Valid @RequestBody MarcarAsistenciaRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.marcar(Ids.deRuta(id), solicitud.asistio(), actor);
    }

    /** HU39: marca a varios inscritos de la misma capacitación en una petición. */
    @PutMapping("/api/capacitaciones/{id}/asistencia")
    @PreAuthorize("hasAuthority('ASISTENCIA_MARCAR')")
    public MarcacionAsistenciaResponse marcarVarias(@PathVariable String id,
            @Valid @RequestBody MarcarAsistenciasRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.marcarVarias(Ids.deRuta(id), solicitud, actor);
    }

    /** HU40: borrado físico que libera el cupo. Responde 204. */
    @DeleteMapping("/api/inscripciones/{id}")
    @PreAuthorize("hasAnyAuthority('INSCRIPCIONES_PROPIAS','INSCRIPCIONES_GESTIONAR')")
    public ResponseEntity<Void> cancelar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        servicio.cancelar(Ids.deRuta(id), actor);
        return ResponseEntity.noContent().build();
    }
}
