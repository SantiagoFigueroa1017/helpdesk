package co.edu.ufpso.helpdesk.membresias.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.membresias.domain.Pago;
import co.edu.ufpso.helpdesk.membresias.domain.ResultadoPago;
import co.edu.ufpso.helpdesk.membresias.dto.ActualizarPagoRequest;
import co.edu.ufpso.helpdesk.membresias.dto.PagoResponse;
import co.edu.ufpso.helpdesk.membresias.dto.RegistrarPagoRequest;
import co.edu.ufpso.helpdesk.membresias.repository.MembresiaRepository;
import co.edu.ufpso.helpdesk.membresias.repository.PagoRepository;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Pagos (HU17 a HU20) y la transacción de bloqueo por falta de pago (contexto 7.5, decidida en 2.5).
 *
 * <p>Efecto de un pago sobre su membresía, en la misma transacción que escribe el pago:
 * <ul>
 *   <li>FALLIDO: la membresía pasa a SUSPENDIDA. La fecha de vencimiento no cambia.</li>
 *   <li>APROBADO: la membresía pasa a ACTIVA y su vigencia crece un mes sin perder días
 *       ({@link Membresia#aplicarPagoAprobado}).</li>
 * </ul>
 * Si una de las dos escrituras falla, MongoDB no aplica ninguna.
 *
 * <p>Cuándo se aplica:
 * <ul>
 *   <li>HU17 (registrar): siempre.</li>
 *   <li>HU19 (editar): solo si cambia el resultado y el pago es el más reciente de su membresía por
 *       {@code fechaPago}. Corregir un pago viejo no toca la membresía actual. Pasar de APROBADO a
 *       FALLIDO suspende, pero no devuelve el mes que ese pago había sumado.</li>
 *   <li>HU20 (eliminar): nunca.</li>
 * </ul>
 */
@Service
public class PagoService {

    /** Zona para sumar meses de calendario (la misma de los reportes, 1.6). */
    static final ZoneId ZONA = ZoneId.of("America/Bogota");

    /** {@code ix_pagos_empresa_fecha} e {@code ix_pagos_resultado_fecha} terminan en {@code fechaPago}. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("fechaPago");

    private final PagoRepository repositorio;
    private final MembresiaRepository membresias;
    private final MongoTemplate template;
    private final Clock reloj;

    public PagoService(PagoRepository repositorio, MembresiaRepository membresias, MongoTemplate template,
            Clock reloj) {
        this.repositorio = repositorio;
        this.membresias = membresias;
        this.template = template;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU18 · Consultar
    // ------------------------------------------------------------------

    /** Por defecto, del más reciente al más antiguo. Un usuario de empresa solo ve los de la suya. */
    public Pagina<PagoResponse> listar(String empresaId, String membresiaId, ResultadoPago resultado,
            Pageable pageable, UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "fechaPago"));

        Query filtro = new Query();
        ObjectId empresa = actor.esDeEmpresa() ? actor.empresaId() : Ids.deParametro("empresaId", empresaId);
        if (empresa != null) {
            filtro.addCriteria(Criteria.where("empresaId").is(empresa));
        }
        ObjectId membresia = Ids.deParametro("membresiaId", membresiaId);
        if (membresia != null) {
            filtro.addCriteria(Criteria.where("membresiaId").is(membresia));
        }
        if (resultado != null) {
            filtro.addCriteria(Criteria.where("resultado").is(resultado));
        }

        // Se cuenta antes de paginar: count respeta skip y limit.
        long total = template.count(filtro, Pago.class);
        List<Pago> pagina = template.find(filtro.with(conOrden), Pago.class);
        return Pagina.de(new PageImpl<>(pagina, conOrden, total), PagoService::aRespuesta);
    }

    public PagoResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        Pago pago = buscar(id);
        if (actor.esDeEmpresa() && !pago.getEmpresaId().equals(actor.empresaId())) {
            throw noExiste();
        }
        return aRespuesta(pago);
    }

    // ------------------------------------------------------------------
    // HU17 · Registrar
    // ------------------------------------------------------------------

    @Transactional
    public PagoResponse registrar(RegistrarPagoRequest solicitud, UsuarioAutenticado actor) {
        Instant ahora = ahora();
        Instant fechaPago = verificarFecha(solicitud.fechaPago(), ahora);
        Membresia membresia = membresias.findById(Ids.deCuerpo(solicitud.membresiaId()))
                .orElseThrow(() -> new ReglaNegocioException("La membresía indicada no existe."));

        Pago pago = new Pago();
        pago.setFechaCreacion(ahora);
        pago.setFechaActualizacion(ahora);
        pago.setMembresiaId(membresia.getId());
        pago.setEmpresaId(membresia.getEmpresaId());
        pago.setMonto(solicitud.monto());
        pago.setFechaPago(fechaPago);
        pago.setMetodoPago(solicitud.metodoPago());
        pago.setReferencia(solicitud.referencia().strip());
        pago.setResultado(solicitud.resultado());
        pago.setRegistradoPorId(actor.id());
        Pago guardado = repositorio.insert(pago);

        aplicarEfecto(membresia, solicitud.resultado(), fechaPago, ahora);
        return aRespuesta(guardado);
    }

    // ------------------------------------------------------------------
    // HU19 · Actualizar
    // ------------------------------------------------------------------

    @Transactional
    public PagoResponse actualizar(ObjectId id, ActualizarPagoRequest solicitud) {
        Instant ahora = ahora();
        Pago pago = buscar(id);
        Instant fechaPago = verificarFecha(solicitud.fechaPago(), ahora);
        boolean cambiaResultado = pago.getResultado() != solicitud.resultado();

        pago.setMonto(solicitud.monto());
        pago.setFechaPago(fechaPago);
        pago.setMetodoPago(solicitud.metodoPago());
        pago.setReferencia(solicitud.referencia().strip());
        pago.setResultado(solicitud.resultado());
        pago.setFechaActualizacion(ahora);
        Pago guardado = repositorio.save(pago);

        if (cambiaResultado && !repositorio.existsByMembresiaIdAndFechaPagoAfterAndIdNot(
                pago.getMembresiaId(), fechaPago, id)) {
            membresias.findById(pago.getMembresiaId())
                    .ifPresent(m -> aplicarEfecto(m, solicitud.resultado(), fechaPago, ahora));
        }
        return aRespuesta(guardado);
    }

    // ------------------------------------------------------------------
    // HU20 · Eliminar
    // ------------------------------------------------------------------

    /** Borrado libre: la membresía queda como está (HU20). */
    public void eliminar(ObjectId id) {
        buscar(id);
        repositorio.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    private void aplicarEfecto(Membresia membresia, ResultadoPago resultado, Instant fechaPago, Instant ahora) {
        if (resultado == ResultadoPago.FALLIDO) {
            membresia.suspenderPorPagoFallido(ahora);
        } else {
            membresia.aplicarPagoAprobado(fechaPago, ZONA, ahora);
        }
        membresias.save(membresia);
    }

    /** Un pago no puede tener fecha futura: sería siempre "el más reciente" y movería la vigencia. */
    private static Instant verificarFecha(Instant fechaPago, Instant ahora) {
        Instant fecha = fechaPago.truncatedTo(ChronoUnit.MILLIS);
        if (fecha.isAfter(ahora)) {
            throw new ReglaNegocioException("La fecha del pago no puede ser futura.");
        }
        return fecha;
    }

    private Pago buscar(ObjectId id) {
        return repositorio.findById(id).orElseThrow(PagoService::noExiste);
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe el pago solicitado.");
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static PagoResponse aRespuesta(Pago p) {
        return new PagoResponse(
                p.getId().toHexString(),
                p.getMembresiaId().toHexString(),
                p.getEmpresaId().toHexString(),
                p.getMonto(),
                p.getFechaPago(),
                p.getMetodoPago().name(),
                p.getReferencia(),
                p.getResultado().name(),
                p.getRegistradoPorId().toHexString(),
                p.getFechaCreacion(),
                p.getFechaActualizacion());
    }
}
