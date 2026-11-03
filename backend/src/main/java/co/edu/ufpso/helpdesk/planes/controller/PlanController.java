package co.edu.ufpso.helpdesk.planes.controller;

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
import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import co.edu.ufpso.helpdesk.planes.dto.ActualizarPlanRequest;
import co.edu.ufpso.helpdesk.planes.dto.CrearPlanRequest;
import co.edu.ufpso.helpdesk.planes.dto.PlanResponse;
import co.edu.ufpso.helpdesk.planes.service.PlanService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/** Planes (HU09 a HU12). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
@RequestMapping("/api/planes")
public class PlanController {

    private final PlanService servicio;

    public PlanController(PlanService servicio) {
        this.servicio = servicio;
    }

    /** HU10: {@code ?activo=true&nivelAcceso=PREMIUM&pagina=0&tamano=20&orden=nombre,asc}. */
    @GetMapping
    @PreAuthorize("hasAuthority('PLANES_VER')")
    public Pagina<PlanResponse> listar(@RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) NivelAcceso nivelAcceso, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(activo, nivelAcceso, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PLANES_VER')")
    public PlanResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU09. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('PLANES_CREAR')")
    public ResponseEntity<PlanResponse> crear(@Valid @RequestBody CrearPlanRequest solicitud) {
        PlanResponse creado = servicio.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/planes/" + creado.id())).body(creado);
    }

    /** HU11. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLANES_EDITAR')")
    public PlanResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarPlanRequest solicitud) {
        return servicio.actualizar(Ids.deRuta(id), solicitud);
    }

    /** HU12. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PLANES_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        servicio.eliminar(Ids.deRuta(id));
        return ResponseEntity.noContent().build();
    }
}
