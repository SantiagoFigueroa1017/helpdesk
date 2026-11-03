package co.edu.ufpso.helpdesk.membresias.controller;

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
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.dto.ActualizarMembresiaRequest;
import co.edu.ufpso.helpdesk.membresias.dto.CrearMembresiaRequest;
import co.edu.ufpso.helpdesk.membresias.dto.MembresiaResponse;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/** Membresías (HU13 a HU16). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
@RequestMapping("/api/membresias")
public class MembresiaController {

    private final MembresiaService servicio;

    public MembresiaController(MembresiaService servicio) {
        this.servicio = servicio;
    }

    /** HU14: {@code ?empresaId=&planId=&estado=SUSPENDIDA&pagina=0&tamano=20}. */
    @GetMapping
    @PreAuthorize("hasAuthority('MEMBRESIAS_VER')")
    public Pagina<MembresiaResponse> listar(@RequestParam(required = false) String empresaId,
            @RequestParam(required = false) String planId, @RequestParam(required = false) EstadoMembresia estado,
            Pageable pageable, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(empresaId, planId, estado, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBRESIAS_VER')")
    public MembresiaResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU13. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('MEMBRESIAS_CREAR')")
    public ResponseEntity<MembresiaResponse> crear(@Valid @RequestBody CrearMembresiaRequest solicitud) {
        MembresiaResponse creada = servicio.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/membresias/" + creada.id())).body(creada);
    }

    /** HU15. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBRESIAS_EDITAR')")
    public MembresiaResponse actualizar(@PathVariable String id,
            @Valid @RequestBody ActualizarMembresiaRequest solicitud) {
        return servicio.actualizar(Ids.deRuta(id), solicitud);
    }

    /** HU16. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBRESIAS_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        servicio.eliminar(Ids.deRuta(id));
        return ResponseEntity.noContent().build();
    }
}
