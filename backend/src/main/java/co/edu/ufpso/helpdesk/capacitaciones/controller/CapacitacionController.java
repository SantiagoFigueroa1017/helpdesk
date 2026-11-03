package co.edu.ufpso.helpdesk.capacitaciones.controller;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.dto.ActualizarCapacitacionRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CrearCapacitacionRequest;
import co.edu.ufpso.helpdesk.capacitaciones.dto.EliminacionCapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.FiltroCapacitaciones;
import co.edu.ufpso.helpdesk.capacitaciones.service.CapacitacionService;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/** Capacitaciones (HU33 a HU36). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
@RequestMapping("/api/capacitaciones")
public class CapacitacionController {

    private final CapacitacionService servicio;

    public CapacitacionController(CapacitacionService servicio) {
        this.servicio = servicio;
    }

    /**
     * HU34: {@code ?modulo=&tipo=&estado=&desde=2026-11-01&hasta=2026-11-30&empresaId=&pagina=0&tamano=20
     * &orden=fechaInicio,asc}. Las fechas son días en hora de Bogotá, las dos inclusivas.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CAPACITACIONES_VER')")
    public Pagina<CapacitacionResponse> listar(@RequestParam(required = false) ModuloErp modulo,
            @RequestParam(required = false) TipoCapacitacion tipo,
            @RequestParam(required = false) EstadoCapacitacion estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String empresaId, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        FiltroCapacitaciones filtro = new FiltroCapacitaciones(modulo, tipo, estado, desde, hasta, empresaId);
        return servicio.listar(filtro, pageable, actor);
    }

    /** HU34. Trae {@code inscritos}, que el frontend usa para confirmar HU36. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CAPACITACIONES_VER')")
    public CapacitacionResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU33: masiva, o personalizada sin solicitud previa. Responde 201 con {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('CAPACITACIONES_CREAR')")
    public ResponseEntity<CapacitacionResponse> crear(@Valid @RequestBody CrearCapacitacionRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        CapacitacionResponse creada = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/capacitaciones/" + creada.id())).body(creada);
    }

    /** HU35: reprogramar o cancelar. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CAPACITACIONES_EDITAR')")
    public CapacitacionResponse actualizar(@PathVariable String id,
            @Valid @RequestBody ActualizarCapacitacionRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizar(Ids.deRuta(id), solicitud, actor);
    }

    /** HU36: borrado físico con sus inscripciones, en una transacción. Responde 200 con lo que se borró. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CAPACITACIONES_ELIMINAR')")
    public EliminacionCapacitacionResponse eliminar(@PathVariable String id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.eliminar(Ids.deRuta(id), actor);
    }
}
