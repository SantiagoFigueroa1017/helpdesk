package co.edu.ufpso.helpdesk.membresias.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección {@code pagos} (HU17 a HU20). Modelo 1.2, sección 6.
 *
 * <ul>
 *   <li>Cada intento de pago es un documento. Crecen sin límite, por eso no se embeben en la
 *       membresía.</li>
 *   <li>{@code empresaId} es copia de la membresía (referencia extendida). Solo la escribe HU17
 *       y no cambia, porque una membresía no cambia de empresa.</li>
 *   <li>{@code monto} se guarda como Decimal128 y no cambia si después cambia el precio del plan.</li>
 * </ul>
 */
@Document("pagos")
public class Pago extends DocumentoBase {

    private ObjectId membresiaId;
    private ObjectId empresaId;
    private BigDecimal monto;
    private Instant fechaPago;
    private MetodoPago metodoPago;
    private String referencia;
    private ResultadoPago resultado;
    private ObjectId registradoPorId;

    public Pago() {
    }

    public ObjectId getMembresiaId() {
        return membresiaId;
    }

    public void setMembresiaId(ObjectId membresiaId) {
        this.membresiaId = membresiaId;
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public Instant getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(Instant fechaPago) {
        this.fechaPago = fechaPago;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public ResultadoPago getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoPago resultado) {
        this.resultado = resultado;
    }

    public ObjectId getRegistradoPorId() {
        return registradoPorId;
    }

    public void setRegistradoPorId(ObjectId registradoPorId) {
        this.registradoPorId = registradoPorId;
    }
}
