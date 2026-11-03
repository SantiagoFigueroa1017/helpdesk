package co.edu.ufpso.helpdesk.capacitaciones.domain;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección {@code inscripciones} (HU37 a HU40). Modelo 1.2, sección 12.
 *
 * <ul>
 *   <li>Una por usuario y capacitación ({@code ux_inscripciones_capacitacion_usuario}).</li>
 *   <li>{@code empresaId} es copia del usuario (referencia extendida). La escribe HU37 y no
 *       cambia, porque un usuario no cambia de empresa.</li>
 *   <li>oneOf de asistencia: {@code asistio}, {@code marcadoPorId} y {@code fechaMarcado} van los
 *       tres en null (sin marcar) o los tres con valor. Son obligatorios aunque valgan null
 *       ({@code @Field(write = ALWAYS)}) y no tienen setters: cambian con
 *       {@link #marcarAsistencia} y {@link #quitarMarca}.</li>
 * </ul>
 */
@Document("inscripciones")
public class Inscripcion extends DocumentoBase {

    private ObjectId capacitacionId;
    private ObjectId usuarioId;
    private ObjectId empresaId;

    /** null = sin marcar (HU39). */
    @Field(write = Field.Write.ALWAYS)
    private Boolean asistio;

    @Field(write = Field.Write.ALWAYS)
    private ObjectId marcadoPorId;

    @Field(write = Field.Write.ALWAYS)
    private Instant fechaMarcado;

    public Inscripcion() {
    }

    /**
     * HU39: el instructor o el administrador marca si asistió, desde la hora de inicio de la
     * sesión (eso último lo revisa el servicio).
     */
    public void marcarAsistencia(boolean asistio, ObjectId marcadoPorId, Instant fechaMarcado) {
        this.asistio = asistio;
        this.marcadoPorId = marcadoPorId;
        this.fechaMarcado = fechaMarcado;
    }

    /** Deja la asistencia sin marcar, los tres campos en null. */
    public void quitarMarca() {
        this.asistio = null;
        this.marcadoPorId = null;
        this.fechaMarcado = null;
    }

    /** Comprueba la regla del oneOf del validador: los tres en null o los tres con valor. */
    public boolean marcaCoherente() {
        boolean sinMarcar = asistio == null && marcadoPorId == null && fechaMarcado == null;
        boolean marcada = asistio != null && marcadoPorId != null && fechaMarcado != null;
        return sinMarcar || marcada;
    }

    public ObjectId getCapacitacionId() {
        return capacitacionId;
    }

    public void setCapacitacionId(ObjectId capacitacionId) {
        this.capacitacionId = capacitacionId;
    }

    public ObjectId getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(ObjectId usuarioId) {
        this.usuarioId = usuarioId;
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public Boolean getAsistio() {
        return asistio;
    }

    public ObjectId getMarcadoPorId() {
        return marcadoPorId;
    }

    public Instant getFechaMarcado() {
        return fechaMarcado;
    }
}
