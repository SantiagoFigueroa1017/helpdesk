package co.edu.ufpso.helpdesk.membresias.domain;

import java.time.Instant;
import java.time.ZoneId;
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

    /**
     * Por qué no hay soporte, o null si lo hay. Mismo criterio que {@link #tieneSoporte(Instant)}:
     * PENDIENTE y SUSPENDIDA salen por su estado; una ACTIVA con la fecha superada sale como VENCIDA.
     */
    public MotivoSinSoporte motivoSinSoporte(Instant ahora) {
        if (estado == EstadoMembresia.PENDIENTE) {
            return MotivoSinSoporte.PENDIENTE;
        }
        if (estado == EstadoMembresia.SUSPENDIDA) {
            return MotivoSinSoporte.SUSPENDIDA;
        }
        return tieneSoporte(ahora) ? null : MotivoSinSoporte.VENCIDA;
    }

    /** Pago FALLIDO (HU17, HU19): la membresía pasa a SUSPENDIDA. La fecha de vencimiento no cambia. */
    public void suspenderPorPagoFallido(Instant ahora) {
        estado = EstadoMembresia.SUSPENDIDA;
        setFechaActualizacion(ahora);
    }

    /**
     * Pago APROBADO (HU17, HU19, decidido en 2.5): la membresía queda ACTIVA y su vigencia crece un
     * mes sin perder días. Si todavía no había vencido en la fecha del pago, el mes se suma a
     * {@code fechaVencimiento}; si ya había vencido, se cuenta desde {@code fechaPago}. El mes es de
     * calendario en la zona indicada (Bogotá), así el 31 de enero pasa al 28 o 29 de febrero.
     */
    public void aplicarPagoAprobado(Instant fechaPago, ZoneId zona, Instant ahora) {
        Instant base = fechaVencimiento != null && fechaVencimiento.isAfter(fechaPago) ? fechaVencimiento : fechaPago;
        fechaVencimiento = base.atZone(zona).plusMonths(1).toInstant();
        estado = EstadoMembresia.ACTIVA;
        setFechaActualizacion(ahora);
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
