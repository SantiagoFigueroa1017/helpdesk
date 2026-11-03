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
import co.edu.ufpso.helpdesk.membresias.domain.ResultadoPago;
import co.edu.ufpso.helpdesk.membresias.dto.ActualizarPagoRequest;
import co.edu.ufpso.helpdesk.membresias.dto.PagoResponse;
import co.edu.ufpso.helpdesk.membresias.dto.RegistrarPagoRequest;
import co.edu.ufpso.helpdesk.membresias.service.PagoService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/**
 * Pagos (HU17 a HU20). Registrar y editar corren la transacción de 7.5. Permisos según
 * {@code modelo/MATRIZ_PERMISOS_2.4.md}.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService servicio;

    public PagoController(PagoService servicio) {
        this.servicio = servicio;
    }

    /** HU18: {@code ?empresaId=&membresiaId=&resultado=FALLIDO&orden=fechaPago,desc}. */
    @GetMapping
    @PreAuthorize("hasAuthority('PAGOS_VER')")
    public Pagina<PagoResponse> listar(@RequestParam(required = false) String empresaId,
            @RequestParam(required = false) String membresiaId, @RequestParam(required = false) ResultadoPago resultado,
            Pageable pageable, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(empresaId, membresiaId, resultado, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_VER')")
    public PagoResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU17. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('PAGOS_REGISTRAR')")
    public ResponseEntity<PagoResponse> registrar(@Valid @RequestBody RegistrarPagoRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        PagoResponse creado = servicio.registrar(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/pagos/" + creado.id())).body(creado);
    }

    /** HU19. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_EDITAR')")
    public PagoResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarPagoRequest solicitud) {
        return servicio.actualizar(Ids.deRuta(id), solicitud);
    }

    /** HU20. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGOS_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        servicio.eliminar(Ids.deRuta(id));
        return ResponseEntity.noContent().build();
    }
}
