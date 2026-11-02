package co.edu.ufpso.helpdesk.tickets.domain;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Detalle de un incidente de Producción (campos del enunciado; fases decididas en 1.3).
 *
 * @param maquinariaId      obligatorio, máximo 50
 * @param codigoLote        obligatorio, máximo 50
 * @param faseProceso       obligatorio
 * @param temperaturaSensor opcional. El validador exige double: el tipo {@code Double} lo
 *                          garantiza aunque el valor sea entero (78.0)
 */
public record DetalleProduccion(String maquinariaId, String codigoLote, FaseProceso faseProceso,
        Double temperaturaSensor) implements DetalleTicket {

    public static final String CAMPO_CLAVE = "maquinariaId";

    public enum FaseProceso {
        PREPARACION,
        MEZCLADO,
        TRANSFORMACION,
        CONTROL_CALIDAD,
        EMPAQUE,
        ALMACENAMIENTO
    }

    @Override
    public ModuloErp modulo() {
        return ModuloErp.PRODUCCION;
    }
}
