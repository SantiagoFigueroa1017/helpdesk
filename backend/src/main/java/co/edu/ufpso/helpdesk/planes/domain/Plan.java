package co.edu.ufpso.helpdesk.planes.domain;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección {@code planes} (HU09 a HU12). Modelo 1.2, sección 4.
 *
 * <p>{@code precio} es mensual (datos de prueba, 1.5) y se guarda como Decimal128 (MongoConfig).
 * El validador exige que sea mayor que cero. Un plan inactivo no se asigna (HU11) y los clientes
 * no lo ven (HU10).
 */
@Document("planes")
public class Plan extends DocumentoBase {

    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private NivelAcceso nivelAcceso;
    private LimitesPlan limites;
    private Boolean activo;

    public Plan() {
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public NivelAcceso getNivelAcceso() {
        return nivelAcceso;
    }

    public void setNivelAcceso(NivelAcceso nivelAcceso) {
        this.nivelAcceso = nivelAcceso;
    }

    public LimitesPlan getLimites() {
        return limites;
    }

    public void setLimites(LimitesPlan limites) {
        this.limites = limites;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
