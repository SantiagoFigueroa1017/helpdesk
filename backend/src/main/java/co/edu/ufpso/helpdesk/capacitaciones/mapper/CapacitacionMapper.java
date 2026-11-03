package co.edu.ufpso.helpdesk.capacitaciones.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.dto.CapacitacionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.InscripcionResponse;
import co.edu.ufpso.helpdesk.capacitaciones.dto.SolicitudResponse;

/**
 * Documentos de capacitaciones, solicitudes e inscripciones a DTO. La API nunca expone los documentos (contexto 8.2).
 */
public final class CapacitacionMapper {

    /** Las fechas sin hora (fecha preferida) se leen y se escriben en hora de Bogotá, como los filtros (2.6). */
    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private CapacitacionMapper() {
    }

    // ---------- ids que hay que nombrar ----------

    public static Set<ObjectId> idsDeUsuarios(Collection<Capacitacion> capacitaciones) {
        Set<ObjectId> ids = new HashSet<>();
        capacitaciones.forEach(c -> ids.add(c.getInstructorId()));
        ids.remove(null);
        return ids;
    }

    public static Set<ObjectId> idsDeEmpresas(Collection<Capacitacion> capacitaciones) {
        Set<ObjectId> ids = new HashSet<>();
        capacitaciones.forEach(c -> ids.add(c.getEmpresaId()));
        ids.remove(null);
        return ids;
    }

    public static Set<ObjectId> idsDeUsuariosDeSolicitudes(Collection<SolicitudCapacitacion> solicitudes) {
        Set<ObjectId> ids = new HashSet<>();
        for (SolicitudCapacitacion s : solicitudes) {
            ids.add(s.getSolicitanteId());
            ids.add(s.getAtendidaPorId());
        }
        ids.remove(null);
        return ids;
    }

    public static Set<ObjectId> idsDeEmpresasDeSolicitudes(Collection<SolicitudCapacitacion> solicitudes) {
        Set<ObjectId> ids = new HashSet<>();
        solicitudes.forEach(s -> ids.add(s.getEmpresaId()));
        return ids;
    }

    public static Set<ObjectId> idsDeUsuariosDeInscripciones(Collection<Inscripcion> inscripciones) {
        Set<ObjectId> ids = new HashSet<>();
        for (Inscripcion i : inscripciones) {
            ids.add(i.getUsuarioId());
            ids.add(i.getMarcadoPorId());
        }
        ids.remove(null);
        return ids;
    }

    public static Set<ObjectId> idsDeEmpresasDeInscripciones(Collection<Inscripcion> inscripciones) {
        Set<ObjectId> ids = new HashSet<>();
        inscripciones.forEach(i -> ids.add(i.getEmpresaId()));
        return ids;
    }

    // ---------- respuestas ----------

    /**
     * @param inscritos       solo en el detalle (HU36); null en el listado
     * @param miInscripcionId inscripción del usuario de la petición, o null
     */
    public static CapacitacionResponse aRespuesta(Capacitacion c, NombresCapacitacion n, Instant ahora, Long inscritos,
            ObjectId miInscripcionId) {
        return new CapacitacionResponse(
                c.getId().toHexString(),
                c.getTitulo(),
                c.getTipo().name(),
                c.getModulo().name(),
                hex(c.getEmpresaId()),
                n.empresa(c.getEmpresaId()),
                c.getFechaInicio(),
                c.getDuracionMinutos(),
                c.getFechaFin(),
                c.getCupo(),
                c.getCuposOcupados(),
                c.cuposDisponibles(),
                c.getInstructorId().toHexString(),
                n.usuario(c.getInstructorId()),
                c.getEstado().name(),
                c.estadoVisible(ahora).name(),
                hex(c.getSolicitudId()),
                inscritos,
                hex(miInscripcionId),
                c.getFechaCreacion(),
                c.getFechaActualizacion());
    }

    public static SolicitudResponse aRespuesta(SolicitudCapacitacion s, NombresCapacitacion n) {
        return new SolicitudResponse(
                s.getId().toHexString(),
                s.getEmpresaId().toHexString(),
                n.empresa(s.getEmpresaId()),
                s.getSolicitanteId().toHexString(),
                n.usuario(s.getSolicitanteId()),
                s.getModulo().name(),
                s.getDescripcion(),
                s.getParticipantesEstimados(),
                dia(s.getFechaPreferida()),
                s.getEstado().name(),
                hex(s.getCapacitacionId()),
                s.getMotivoRechazo(),
                hex(s.getAtendidaPorId()),
                n.usuario(s.getAtendidaPorId()),
                s.getFechaCreacion(),
                s.getFechaActualizacion());
    }

    /**
     * @param c la capacitación de la inscripción, leída con proyección; null si ya no existe (no debería:
     *          HU36 borra sus inscripciones en la misma transacción)
     */
    public static InscripcionResponse aRespuesta(Inscripcion i, Capacitacion c, NombresCapacitacion n, Instant ahora) {
        return new InscripcionResponse(
                i.getId().toHexString(),
                i.getCapacitacionId().toHexString(),
                c == null ? null : c.getTitulo(),
                c == null ? null : c.getModulo().name(),
                c == null ? null : c.getFechaInicio(),
                c == null ? null : c.getFechaFin(),
                c == null ? null : c.estadoVisible(ahora).name(),
                i.getUsuarioId().toHexString(),
                n.usuario(i.getUsuarioId()),
                i.getEmpresaId().toHexString(),
                n.empresa(i.getEmpresaId()),
                i.getAsistio(),
                hex(i.getMarcadoPorId()),
                n.usuario(i.getMarcadoPorId()),
                i.getFechaMarcado(),
                i.getFechaCreacion());
    }

    /** Un día a la medianoche de Bogotá, que es como se guarda la fecha preferida. */
    public static Instant inicioDelDia(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(BOGOTA).toInstant();
    }

    private static LocalDate dia(Instant fecha) {
        return fecha == null ? null : LocalDate.ofInstant(fecha, BOGOTA);
    }

    private static String hex(ObjectId id) {
        return id == null ? null : id.toHexString();
    }
}
