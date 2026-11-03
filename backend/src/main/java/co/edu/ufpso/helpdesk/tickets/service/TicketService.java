package co.edu.ufpso.helpdesk.tickets.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ErrorCampo;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.membresias.service.ContratoEmpresa;
import co.edu.ufpso.helpdesk.membresias.service.MembresiaService;
import co.edu.ufpso.helpdesk.membresias.service.SoporteService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.dto.CamposModuloResponse;
import co.edu.ufpso.helpdesk.tickets.dto.CrearTicketRequest;
import co.edu.ufpso.helpdesk.tickets.dto.TicketResponse;
import co.edu.ufpso.helpdesk.tickets.mapper.DetalleTicketMapper;
import co.edu.ufpso.helpdesk.tickets.mapper.TicketMapper;
import co.edu.ufpso.helpdesk.tickets.repository.TicketRepository;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesion;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesionService;

/**
 * Tickets polimórficos (HU21 a HU24, modelo 1.2 sección 7, tarea 2.6).
 *
 * <p>Crear (HU21):
 * <ul>
 *   <li>Usuario de empresa: crea en su empresa y siempre es el solicitante (HU21). Si envía otro
 *       solicitante, 422.</li>
 *   <li>Personal de soporte (GLOBAL): crea en nombre de un cliente y debe indicar el solicitante, que
 *       existe, está activo y pertenece a una empresa. La empresa del ticket es la del solicitante.</li>
 *   <li>Soporte (contexto 7.5): sin membresía ACTIVA y vigente responde 403 {@code SOPORTE_BLOQUEADO}
 *       con {@code motivo}, también cuando crea un agente (decidido en 2.5).</li>
 *   <li>El módulo debe estar contratado en la membresía: 422 (HU21 v.4).</li>
 *   <li>Nace ABIERTO, sin agente, con la primera entrada del historial y la fecha límite de
 *       respuesta = creación + {@code horasRespuesta} del plan. No cambia si el plan cambia (HU11).</li>
 * </ul>
 */
@Service
public class TicketService {

    private final TicketRepository repositorio;
    private final SoporteService soporte;
    private final MembresiaService membresias;
    private final UsuarioSesionService usuarios;
    private final Clock reloj;

    public TicketService(TicketRepository repositorio, SoporteService soporte, MembresiaService membresias,
            UsuarioSesionService usuarios, Clock reloj) {
        this.repositorio = repositorio;
        this.soporte = soporte;
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Formulario dinámico
    // ------------------------------------------------------------------

    /** Campos del {@code detalle} de cada módulo. Es fijo: sale del catálogo, no de la base. */
    public List<CamposModuloResponse> campos() {
        return TicketMapper.campos();
    }

    // ------------------------------------------------------------------
    // HU21 · Crear
    // ------------------------------------------------------------------

    public TicketResponse crear(CrearTicketRequest solicitud, UsuarioAutenticado actor) {
        ObjectId empresaId;
        ObjectId solicitanteId;
        if (actor.esDeEmpresa()) {
            soporte.exigirSoporte(actor);
            ObjectId pedido = solicitud.solicitanteId() == null ? null : Ids.deCuerpo(solicitud.solicitanteId());
            if (pedido != null && !pedido.equals(actor.id())) {
                throw new ReglaNegocioException("Solo el personal de soporte crea tickets en nombre de otro usuario. "
                        + "Deje el solicitante vacío.");
            }
            empresaId = actor.empresaId();
            solicitanteId = actor.id();
        } else {
            UsuarioSesion solicitante = solicitanteDeAgente(solicitud.solicitanteId());
            // El agente necesita la empresa del solicitante para saber qué membresía revisar.
            soporte.exigirSoporteEmpresa(solicitante.empresaId());
            empresaId = solicitante.empresaId();
            solicitanteId = solicitante.id();
        }

        DetalleTicket detalle = DetalleTicketMapper.desdeSolicitud(solicitud.modulo(), solicitud.tipo(),
                solicitud.detalle());
        ContratoEmpresa contrato = membresias.contratoDeEmpresa(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("La membresía de la empresa no tiene un plan vigente."));
        verificarModuloContratado(solicitud.modulo(), contrato);

        Instant ahora = ahora();
        Ticket ticket = new Ticket();
        ticket.setFechaCreacion(ahora);
        ticket.setFechaActualizacion(ahora);
        ticket.setEmpresaId(empresaId);
        ticket.setCreadorId(actor.id());
        ticket.setSolicitanteId(solicitanteId);
        ticket.setAgenteId(null);
        ticket.setModulo(solicitud.modulo());
        ticket.setTipo(solicitud.tipo());
        ticket.setTitulo(solicitud.titulo().strip());
        ticket.setDescripcion(solicitud.descripcion().strip());
        ticket.setDetalle(detalle);
        ticket.setEstado(EstadoTicket.ABIERTO);
        ticket.setHistorialEstados(new ArrayList<>(List.of(new CambioEstado(EstadoTicket.ABIERTO, ahora, actor.id()))));
        ticket.setFechaResolucion(null);
        ticket.setFechaPrimeraAsignacion(null);
        ticket.setFechaLimiteRespuesta(ahora.plus(Duration.ofHours(contrato.plan().limites().horasRespuesta())));
        verificarCoherencia(ticket);
        return TicketMapper.aRespuesta(repositorio.insert(ticket));
    }

    /** El solicitante que indica un agente: obligatorio, existe, activo y de una empresa cliente. */
    private UsuarioSesion solicitanteDeAgente(String solicitanteId) {
        if (solicitanteId == null) {
            throw new ApiException(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                    Map.of("errores", List.of(new ErrorCampo("solicitanteId",
                            "Indique el cliente al que pertenece el ticket."))));
        }
        UsuarioSesion solicitante = usuarios.buscarParaSesion(Ids.deCuerpo(solicitanteId))
                .orElseThrow(() -> new ReglaNegocioException("El solicitante indicado no existe."));
        if (solicitante.empresaId() == null) {
            throw new ReglaNegocioException("El solicitante debe ser un usuario de una empresa cliente.");
        }
        if (!solicitante.activo()) {
            throw new ReglaNegocioException("El solicitante está inactivo.");
        }
        return solicitante;
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    private static void verificarModuloContratado(ModuloErp modulo, ContratoEmpresa contrato) {
        if (!contrato.modulos().contains(modulo)) {
            String contratados = EnumSet.copyOf(contrato.modulos()).stream()
                    .map(m -> CatalogoDetalle.de(m).etiqueta())
                    .collect(Collectors.joining(", "));
            throw new ReglaNegocioException("La empresa no tiene contratado el módulo "
                    + CatalogoDetalle.de(modulo).etiqueta() + ". Módulos contratados: " + contratados + ".");
        }
    }

    /**
     * Misma regla del {@code oneOf}: responde 422 antes de que MongoDB rechace el documento con un
     * 500. Con el mapper no debería fallar nunca; es la red de seguridad de 2.3.
     */
    private static void verificarCoherencia(Ticket ticket) {
        if (!ticket.detalleCoherente()) {
            throw new ReglaNegocioException("Los campos específicos no corresponden al módulo y tipo del ticket.");
        }
        if (ticket.getHistorialEstados().size() > Ticket.MAX_HISTORIAL) {
            throw new ReglaNegocioException("El historial del ticket llegó al máximo de "
                    + Ticket.MAX_HISTORIAL + " cambios de estado.");
        }
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
