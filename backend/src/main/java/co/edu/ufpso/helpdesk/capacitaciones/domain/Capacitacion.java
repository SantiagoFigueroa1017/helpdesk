package co.edu.ufpso.helpdesk.capacitaciones.domain;

import java.time.Duration;
import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Documento de la colección {@code capacitaciones} (HU33 a HU36). Modelo 1.2, sección 10.
 *
 * <ul>
 *   <li>oneOf por {@code tipo}: una MASIVA lleva {@code empresaId} y {@code solicitudId} en null;
 *       una PERSONALIZADA lleva {@code empresaId}, y {@code solicitudId} solo si nació de una
 *       solicitud (1.3). Los dos son obligatorios aunque valgan null, por eso llevan
 *       {@code @Field(write = ALWAYS)}.</li>
 *   <li>{@code fechaFin} = {@code fechaInicio} + {@code duracionMinutos}. No tiene setter: se
 *       recalcula al cambiar cualquiera de los dos.</li>
 *   <li>{@code cuposOcupados} es un contador. Inscribirse (HU37) lo sube en la misma transacción
 *       que inserta la inscripción, con la condición {@code cuposOcupados < cupo}.</li>
 *   <li>Lo que compara dos campos ({@code cupo} no menor que {@code cuposOcupados},
 *       {@code fechaInicio} no anterior a hoy) lo revisa el servicio.</li>
 * </ul>
 */
@Document("capacitaciones")
public class Capacitacion extends DocumentoBase {

    private String titulo;
    private TipoCapacitacion tipo;
    private ModuloErp modulo;

    /** null en las MASIVA. */
    @Field(write = Field.Write.ALWAYS)
    private ObjectId empresaId;

    private Instant fechaInicio;
    private Integer duracionMinutos;
    private Instant fechaFin;
    private Integer cupo;
    private Integer cuposOcupados = 0;
    private ObjectId instructorId;
    private EstadoCapacitacion estado = EstadoCapacitacion.PROGRAMADA;

    /** Solicitud que originó una PERSONALIZADA. null en las MASIVA y en las personalizadas sin solicitud. */
    @Field(write = Field.Write.ALWAYS)
    private ObjectId solicitudId;

    public Capacitacion() {
    }

    /**
     * Comprueba la regla del oneOf del validador. El servicio la revisa antes de guardar para
     * responder 422 en vez de un 500.
     */
    public boolean tipoCoherente() {
        if (tipo == TipoCapacitacion.MASIVA) {
            return empresaId == null && solicitudId == null;
        }
        return tipo == TipoCapacitacion.PERSONALIZADA && empresaId != null;
    }

    /** Cupos disponibles que muestra HU34. */
    public int cuposDisponibles() {
        return cupo - cuposOcupados;
    }

    /** "Realizada" (derivada): programada y con {@code fechaFin} ya pasada. */
    public boolean realizada(Instant ahora) {
        return estado == EstadoCapacitacion.PROGRAMADA && fechaFin != null && fechaFin.isBefore(ahora);
    }

    private void recalcularFechaFin() {
        this.fechaFin = (fechaInicio == null || duracionMinutos == null)
                ? null
                : fechaInicio.plus(Duration.ofMinutes(duracionMinutos));
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public TipoCapacitacion getTipo() {
        return tipo;
    }

    public void setTipo(TipoCapacitacion tipo) {
        this.tipo = tipo;
    }

    public ModuloErp getModulo() {
        return modulo;
    }

    public void setModulo(ModuloErp modulo) {
        this.modulo = modulo;
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public Instant getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Instant fechaInicio) {
        this.fechaInicio = fechaInicio;
        recalcularFechaFin();
    }

    public Integer getDuracionMinutos() {
        return duracionMinutos;
    }

    public void setDuracionMinutos(Integer duracionMinutos) {
        this.duracionMinutos = duracionMinutos;
        recalcularFechaFin();
    }

    public Instant getFechaFin() {
        return fechaFin;
    }

    public Integer getCupo() {
        return cupo;
    }

    public void setCupo(Integer cupo) {
        this.cupo = cupo;
    }

    public Integer getCuposOcupados() {
        return cuposOcupados;
    }

    public void setCuposOcupados(Integer cuposOcupados) {
        this.cuposOcupados = cuposOcupados;
    }

    public ObjectId getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(ObjectId instructorId) {
        this.instructorId = instructorId;
    }

    public EstadoCapacitacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoCapacitacion estado) {
        this.estado = estado;
    }

    public ObjectId getSolicitudId() {
        return solicitudId;
    }

    public void setSolicitudId(ObjectId solicitudId) {
        this.solicitudId = solicitudId;
    }
}
