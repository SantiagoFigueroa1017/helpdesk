package co.edu.ufpso.helpdesk.tickets.domain;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Detalle de un incidente de Inventarios (campos decididos por el equipo en 1.2).
 *
 * @param codigoBodega      obligatorio, máximo 50
 * @param codigoProducto    obligatorio, máximo 50
 * @param tipoOperacion     obligatorio
 * @param codigoBarras      opcional, máximo 50
 * @param existenciaSistema opcional, int32 (puede ser negativo: es lo que muestra el sistema)
 */
public record DetalleInventarios(String codigoBodega, String codigoProducto, TipoOperacion tipoOperacion,
        String codigoBarras, Integer existenciaSistema) implements DetalleTicket {

    public static final String CAMPO_CLAVE = "codigoBodega";

    public enum TipoOperacion {
        ENTRADA,
        SALIDA,
        TRASLADO,
        AJUSTE,
        ALERTA_STOCK
    }

    @Override
    public ModuloErp modulo() {
        return ModuloErp.INVENTARIOS;
    }
}
