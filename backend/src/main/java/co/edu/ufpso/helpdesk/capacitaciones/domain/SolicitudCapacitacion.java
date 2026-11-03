package co.edu.ufpso.helpdesk.capacitaciones.domain;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Documento de la colección {@code solicitudesCapacitacion} (CRUD fuera de las 40). Modelo 1.2,
 * sección 11.
 *
 * <p>oneOf por {@code estado}:
 * <ul>
 *   <li>PENDIENTE: {@code capacitacionId} y {@code motivoRechazo} en null. Puede conservar
 *       {@code atendidaPorId} si volvió a pendiente por HU36 v.4.</li>
 *   <li>PROGRAMADA: {@code capacitacionId} y {@code atendidaPorId} con valor, sin motivo.</li>
 *   <li>RECHAZADA: {@code motivoRechazo} y {@code atendidaPorId} con valor, sin capacitación.</li>
 * </ul>
 * Los tres campos son obligatorios aunque valgan null ({@code @Field(write = ALWAYS)}) y no tienen
 * setters: cambian con {@link #programar}, {@link #rechazar} y {@link #devolverAPendiente}.
 * {@code fechaPreferida} es lo contrario: opcional, y no se escribe si es null.
 */
@Document("solicitudesCapacitacion")
public class SolicitudCapacitacion extends DocumentoBase {

    private ObjectId empresaId;
    private ObjectId solicitanteId;
    private ModuloErp modulo;
    private String descripcion;
    private Integer participantesEstimados;
    private Instant fechaPreferida;
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Field(write = Field.Write.ALWAYS)
    private ObjectId capacitacionId;

    @Field(write = Field.Write.ALWAYS)
    private String motivoRechazo;

    @Field(write = Field.Write.ALWAYS)
    private ObjectId atendidaPorId;

    public SolicitudCapacitacion() {
    }

    /** HU33: el agente la programa. Va en la misma transacción que crea la capacitación. */
    public void programar(ObjectId capacitacionId, ObjectId atendidaPorId) {
        this.estado = EstadoSolicitud.PROGRAMADA;
        this.capacitacionId = capacitacionId;
        this.motivoRechazo = null;
        this.atendidaPorId = atendidaPorId;
    }

    /** El agente la rechaza con un motivo. */
    public void rechazar(String motivoRechazo, ObjectId atendidaPorId) {
        this.estado = EstadoSolicitud.RECHAZADA;
        this.capacitacionId = null;
        this.motivoRechazo = motivoRechazo;
        this.atendidaPorId = atendidaPorId;
    }

    /**
     * HU36 v.4: al eliminar la capacitación que la programó, la solicitud vuelve a PENDIENTE sin
     * capacitación. Conserva {@code atendidaPorId}.
     */
    public void devolverAPendiente() {
        this.estado = EstadoSolicitud.PENDIENTE;
        this.capacitacionId = null;
        this.motivoRechazo = null;
    }

    /** Comprueba la regla del oneOf del validador. */
    public boolean estadoCoherente() {
        if (estado == null) {
            return false;
        }
        return switch (estado) {
            case PENDIENTE -> capacitacionId == null && motivoRechazo == null;
            case PROGRAMADA -> capacitacionId != null && motivoRechazo == null && atendidaPorId != null;
            case RECHAZADA -> capacitacionId == null && motivoRechazo != null && !motivoRechazo.isBlank()
                    && atendidaPorId != null;
        };
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public ObjectId getSolicitanteId() {
        return solicitanteId;
    }

    public void setSolicitanteId(ObjectId solicitanteId) {
        this.solicitanteId = solicitanteId;
    }

    public ModuloErp getModulo() {
        return modulo;
    }

    public void setModulo(ModuloErp modulo) {
        this.modulo = modulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getParticipantesEstimados() {
        return participantesEstimados;
    }

    public void setParticipantesEstimados(Integer participantesEstimados) {
        this.participantesEstimados = participantesEstimados;
    }

    public Instant getFechaPreferida() {
        return fechaPreferida;
    }

    public void setFechaPreferida(Instant fechaPreferida) {
        this.fechaPreferida = fechaPreferida;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public ObjectId getCapacitacionId() {
        return capacitacionId;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public ObjectId getAtendidaPorId() {
        return atendidaPorId;
    }
}
