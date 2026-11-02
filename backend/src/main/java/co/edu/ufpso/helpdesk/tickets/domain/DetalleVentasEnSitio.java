package co.edu.ufpso.helpdesk.tickets.domain;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Detalle de un incidente de Ventas en Sitio, caja o POS (campos decididos por el equipo en 1.2).
 *
 * @param cajaId        obligatorio, máximo 50
 * @param turnoId       obligatorio, máximo 50
 * @param dispositivo   obligatorio
 * @param numeroFactura opcional, máximo 50
 * @param medioPago     opcional
 */
public record DetalleVentasEnSitio(String cajaId, String turnoId, Dispositivo dispositivo,
        String numeroFactura, MedioPago medioPago) implements DetalleTicket {

    public static final String CAMPO_CLAVE = "cajaId";

    public enum Dispositivo {
        IMPRESORA,
        LECTOR_CODIGO,
        CAJON_MONEDERO,
        DATAFONO,
        NINGUNO
    }

    public enum MedioPago {
        EFECTIVO,
        TARJETA,
        TRANSFERENCIA,
        MIXTO
    }

    @Override
    public ModuloErp modulo() {
        return ModuloErp.VENTAS_EN_SITIO;
    }
}
