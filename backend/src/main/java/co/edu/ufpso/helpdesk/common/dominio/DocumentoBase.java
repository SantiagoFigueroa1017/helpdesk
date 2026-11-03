package co.edu.ufpso.helpdesk.common.dominio;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

/**
 * Campos que llevan los documentos de las 13 colecciones (modelo 1.2, convenciones):
 * {@code _id}, {@code schemaVersion}, {@code fechaCreacion} y {@code fechaActualizacion}.
 *
 * <p>Spring Data mapea los campos de la superclase como si fueran de cada documento. No se
 * escribe {@code _class} (decisión de 2.3), así que la herencia no deja rastro en MongoDB.
 *
 * <p>Las fechas las pone el servicio al crear y al actualizar. Hoy {@code schemaVersion} vale 1
 * en todas las colecciones; si una cambia de esquema, su documento sobrescribe el valor inicial.
 */
public abstract class DocumentoBase {

    /** Versión de esquema vigente en todas las colecciones (contexto 7.3). */
    public static final int SCHEMA_VERSION_INICIAL = 1;

    @Id
    private ObjectId id;

    private Integer schemaVersion = SCHEMA_VERSION_INICIAL;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;

    protected DocumentoBase() {
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
