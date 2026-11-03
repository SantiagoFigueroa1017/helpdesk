package co.edu.ufpso.helpdesk.tickets.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Documento de la colección {@code tickets} (HU21 a HU24). Modelo 1.2, sección 7.
 *
 * <p>Reglas de escritura que impone el validador {@code $jsonSchema}:
 * <ul>
 *   <li>{@code agenteId}, {@code fechaResolucion} y {@code fechaPrimeraAsignacion} son
 *       obligatorios aunque valgan null. Spring Data omite los null por defecto, así que llevan
 *       {@code @Field(write = ALWAYS)}.</li>
 *   <li>{@code detalle} es lo contrario: un INCIDENTE lo exige y una SUGERENCIA no puede traerlo
 *       ni siquiera en null. Se deja con la escritura por defecto (un null no se escribe).</li>
 *   <li>Los opcionales del detalle (por ejemplo {@code logApi}) tampoco se escriben si son null.</li>
 * </ul>
 *
 * <p>No tiene {@code @Version}: el validador no admite campos fuera del modelo.
 *
 * <p>Esta clase nunca sale por la API (contexto 8.2). Los controladores reciben y devuelven DTO.
 */
@Document("tickets")
public class Ticket extends DocumentoBase {

    /** Tope de {@code historialEstados} en el validador. */
    public static final int MAX_HISTORIAL = 50;

    private ObjectId empresaId;
    private ObjectId creadorId;
    private ObjectId solicitanteId;

    /** null = sin asignar. */
    @Field(write = Field.Write.ALWAYS)
    private ObjectId agenteId;

    private ModuloErp modulo;
    private TipoTicket tipo;
    private String titulo;
    private String descripcion;

    /** Solo en incidentes. null en sugerencias, y entonces no se escribe. */
    private DetalleTicket detalle;

    private EstadoTicket estado;
    private List<CambioEstado> historialEstados = new ArrayList<>();

    @Field(write = Field.Write.ALWAYS)
    private Instant fechaResolucion;

    private Instant fechaLimiteRespuesta;

    @Field(write = Field.Write.ALWAYS)
    private Instant fechaPrimeraAsignacion;

    public Ticket() {
    }

    /**
     * Comprueba la misma regla que el {@code oneOf} del validador: un INCIDENTE trae el detalle de
     * su módulo y una SUGERENCIA no trae detalle. El servicio la revisa antes de guardar para
     * responder 422 en vez de dejar que MongoDB rechace el documento con un 500.
     */
    public boolean detalleCoherente() {
        if (tipo == TipoTicket.SUGERENCIA) {
            return detalle == null;
        }
        return tipo == TipoTicket.INCIDENTE && detalle != null && detalle.modulo() == modulo;
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public ObjectId getCreadorId() {
        return creadorId;
    }

    public void setCreadorId(ObjectId creadorId) {
        this.creadorId = creadorId;
    }

    public ObjectId getSolicitanteId() {
        return solicitanteId;
    }

    public void setSolicitanteId(ObjectId solicitanteId) {
        this.solicitanteId = solicitanteId;
    }

    public ObjectId getAgenteId() {
        return agenteId;
    }

    public void setAgenteId(ObjectId agenteId) {
        this.agenteId = agenteId;
    }

    public ModuloErp getModulo() {
        return modulo;
    }

    public void setModulo(ModuloErp modulo) {
        this.modulo = modulo;
    }

    public TipoTicket getTipo() {
        return tipo;
    }

    public void setTipo(TipoTicket tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public DetalleTicket getDetalle() {
        return detalle;
    }

    public void setDetalle(DetalleTicket detalle) {
        this.detalle = detalle;
    }

    public EstadoTicket getEstado() {
        return estado;
    }

    public void setEstado(EstadoTicket estado) {
        this.estado = estado;
    }

    public List<CambioEstado> getHistorialEstados() {
        return historialEstados;
    }

    public void setHistorialEstados(List<CambioEstado> historialEstados) {
        this.historialEstados = historialEstados;
    }

    public Instant getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(Instant fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public Instant getFechaLimiteRespuesta() {
        return fechaLimiteRespuesta;
    }

    public void setFechaLimiteRespuesta(Instant fechaLimiteRespuesta) {
        this.fechaLimiteRespuesta = fechaLimiteRespuesta;
    }

    public Instant getFechaPrimeraAsignacion() {
        return fechaPrimeraAsignacion;
    }

    public void setFechaPrimeraAsignacion(Instant fechaPrimeraAsignacion) {
        this.fechaPrimeraAsignacion = fechaPrimeraAsignacion;
    }
}
