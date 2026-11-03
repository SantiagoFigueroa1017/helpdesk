package co.edu.ufpso.helpdesk.tickets.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.CrearTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
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

    /** HU21. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('TICKETS_CREAR')")
    public ResponseEntity<TicketResponse> crear(@Valid @RequestBody CrearTicketRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        TicketResponse creado = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/tickets/" + creado.id())).body(creado);
    }
}
