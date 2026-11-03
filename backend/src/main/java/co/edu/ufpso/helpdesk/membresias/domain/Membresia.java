package co.edu.ufpso.helpdesk.membresias.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Documento de la colección {@code membresias} (HU13 a HU16). Modelo 1.2, sección 5.
 *
 * <ul>
 *   <li>Una por empresa ({@code ux_membresias_empresa}). {@code empresaId} no cambia en HU15.</li>
 *   <li>{@code modulosContratados}: de 1 a 5, sin repetidos. El tope real lo fija
 *       {@code limites.maxModulos} del plan (servicio).</li>
 *   <li>{@code fechaVencimiento} posterior a {@code fechaInicio}: lo revisa el servicio, porque
 *       {@code $jsonSchema} no compara dos campos.</li>
 * </ul>
 */
@Document("membresias")
public class Membresia extends DocumentoBase {

    private ObjectId empresaId;
    private ObjectId planId;
    private List<ModuloErp> modulosContratados = new ArrayList<>();
    private Instant fechaInicio;
    private Instant fechaVencimiento;
    private EstadoMembresia estado;

    public Membresia() {
    }

    /**
     * Regla de soporte (contexto 7.5, HU13 v.4): la membresía está ACTIVA y {@code ahora} no supera
     * {@code fechaVencimiento}. La verificación central de la 2.5 la usa en cada petición de un
     * usuario de alcance EMPRESA; no hay que guardar un estado VENCIDA.
     */
    public boolean tieneSoporte(Instant ahora) {
        return estado == EstadoMembresia.ACTIVA
                && fechaVencimiento != null
                && !ahora.isAfter(fechaVencimiento);
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public ObjectId getPlanId() {
        return planId;
    }

    public void setPlanId(ObjectId planId) {
        this.planId = planId;
    }

    public List<ModuloErp> getModulosContratados() {
        return modulosContratados;
    }

    public void setModulosContratados(List<ModuloErp> modulosContratados) {
        this.modulosContratados = modulosContratados;
    }

    public Instant getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Instant fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Instant getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(Instant fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public EstadoMembresia getEstado() {
        return estado;
    }

    public void setEstado(EstadoMembresia estado) {
        this.estado = estado;
    }
}
