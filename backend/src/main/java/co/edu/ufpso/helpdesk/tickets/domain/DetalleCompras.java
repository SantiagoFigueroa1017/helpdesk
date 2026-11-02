package co.edu.ufpso.helpdesk.tickets.domain;

import java.math.BigDecimal;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Detalle de un incidente de Compras (campos decididos por el equipo en 1.2).
 *
 * @param nitProveedor           obligatorio, máximo 50
 * @param etapa                  obligatorio
 * @param numeroOrdenCompra      opcional, máximo 50
 * @param numeroFacturaProveedor opcional, máximo 50
 * @param montoInvolucrado       opcional. Se guarda como Decimal128 (MongoConfig)
 */
public record DetalleCompras(String nitProveedor, Etapa etapa, String numeroOrdenCompra,
        String numeroFacturaProveedor, BigDecimal montoInvolucrado) implements DetalleTicket {

    public static final String CAMPO_CLAVE = "nitProveedor";

    public enum Etapa {
        ORDEN_COMPRA,
        RECEPCION,
        CUENTA_POR_PAGAR,
        PAGO_PROVEEDOR
    }

    @Override
    public ModuloErp modulo() {
        return ModuloErp.COMPRAS;
    }
}
