package co.edu.ufpso.helpdesk.tickets.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.CrearTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.FiltroTickets;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResumenResponse;
import co.edu.ufpso.helpdesk.tickets.service.TicketService;
import jakarta.validation.Valid;

/** Tickets (HU21 a HU24). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService servicio;

    public TicketController(TicketService servicio) {
        this.servicio = servicio;
    }

    /**
     * Definición del formulario dinámico (HU21). Solo exige estar autenticado: no tiene datos de
     * nadie y la usan tanto el formulario como el detalle del ticket.
     */
    @GetMapping("/campos")
    public List<CamposModuloResponse> campos() {
        return servicio.campos();
    }

    /**
     * HU22: {@code ?estado=&modulo=&tipo=&desde=2026-10-01&hasta=2026-10-31&empresaId=&agenteId=
     * &sinAsignar=true&pagina=0&tamano=20&orden=fechaLimiteRespuesta,asc}. Las fechas son días, en
     * hora de Bogotá, y las dos son inclusivas.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('TICKETS_VER','TICKETS_VER_PROPIOS')")
    public Pagina<TicketResumenResponse> listar(@RequestParam(required = false) EstadoTicket estado,
            @RequestParam(required = false) ModuloErp modulo, @RequestParam(required = false) TipoTicket tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String empresaId, @RequestParam(required = false) String agenteId,
            @RequestParam(required = false) Boolean sinAsignar, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        FiltroTickets filtro = new FiltroTickets(estado, modulo, tipo, desde, hasta, empresaId, agenteId, sinAsignar);
        return servicio.listar(filtro, pageable, actor);
    }

    /** HU22: campos comunes, campos del módulo e historial. Un ticket ajeno responde 404. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TICKETS_VER','TICKETS_VER_PROPIOS')")
    public TicketResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU21. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('TICKETS_CREAR')")
    public ResponseEntity<TicketResponse> crear(@Valid @RequestBody CrearTicketRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        TicketResponse creado = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/tickets/" + creado.id())).body(creado);
    }
}
