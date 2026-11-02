package co.edu.ufpso.helpdesk.tickets.domain;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Detalle de un incidente de Ventas Online (campos del enunciado).
 *
 * @param pasarelaPagoId obligatorio, máximo 50
 * @param urlError       obligatorio, máximo 2048
 * @param navegador      obligatorio, máximo 100
 * @param logApi         opcional (null = no se guarda), máximo 20000
 */
public record DetalleVentasOnline(String pasarelaPagoId, String urlError, String navegador, String logApi)
        implements DetalleTicket {

    /** Campo que solo existe en este detalle; con él se reconoce al leer. */
    public static final String CAMPO_CLAVE = "pasarelaPagoId";

    @Override
    public ModuloErp modulo() {
        return ModuloErp.VENTAS_ONLINE;
    }
}
