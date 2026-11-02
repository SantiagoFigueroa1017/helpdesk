package co.edu.ufpso.helpdesk.tickets.repository;

import java.math.BigDecimal;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import co.edu.ufpso.helpdesk.tickets.domain.DetalleCompras;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleInventarios;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleProduccion;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasEnSitio;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasOnline;

/**
 * Lee {@code tickets.detalle} sin {@code _class} (decisión de 2.3, opción a).
 *
 * <p>El tipo se reconoce por el campo que solo existe en un detalle: {@code pasarelaPagoId},
 * {@code maquinariaId}, {@code codigoBodega}, {@code nitProveedor} o {@code cajaId}. Es una regla
 * exacta porque el validador exige ese campo en su rama del {@code oneOf} y, con
 * {@code additionalProperties: false}, lo prohíbe en las otras cuatro. Sirve igual para los
 * documentos que escribe el backend y para los que se cargaron desde mongosh (la semilla).
 *
 * <p>Los números se leen como {@link Number} para no depender de si llegaron como int32,
 * int64 o double; el validador ya garantiza el tipo exacto al escribir.
 *
 * <p>Se registra en {@code MongoConfig.mongoCustomConversions()}.
 */
@ReadingConverter
public class DetalleTicketLecturaConverter implements Converter<Document, DetalleTicket> {

    @Override
    public DetalleTicket convert(Document d) {
        if (d.containsKey(DetalleVentasOnline.CAMPO_CLAVE)) {
            return new DetalleVentasOnline(
                    d.getString("pasarelaPagoId"),
                    d.getString("urlError"),
                    d.getString("navegador"),
                    d.getString("logApi"));
        }
        if (d.containsKey(DetalleProduccion.CAMPO_CLAVE)) {
            return new DetalleProduccion(
                    d.getString("maquinariaId"),
                    d.getString("codigoLote"),
                    enumDe(DetalleProduccion.FaseProceso.class, d.getString("faseProceso")),
                    doubleDe(d.get("temperaturaSensor")));
        }
        if (d.containsKey(DetalleInventarios.CAMPO_CLAVE)) {
            return new DetalleInventarios(
                    d.getString("codigoBodega"),
                    d.getString("codigoProducto"),
                    enumDe(DetalleInventarios.TipoOperacion.class, d.getString("tipoOperacion")),
                    d.getString("codigoBarras"),
                    enteroDe(d.get("existenciaSistema")));
        }
        if (d.containsKey(DetalleCompras.CAMPO_CLAVE)) {
            return new DetalleCompras(
                    d.getString("nitProveedor"),
                    enumDe(DetalleCompras.Etapa.class, d.getString("etapa")),
                    d.getString("numeroOrdenCompra"),
                    d.getString("numeroFacturaProveedor"),
                    decimalDe(d.get("montoInvolucrado")));
        }
        if (d.containsKey(DetalleVentasEnSitio.CAMPO_CLAVE)) {
            return new DetalleVentasEnSitio(
                    d.getString("cajaId"),
                    d.getString("turnoId"),
                    enumDe(DetalleVentasEnSitio.Dispositivo.class, d.getString("dispositivo")),
                    d.getString("numeroFactura"),
                    enumDe(DetalleVentasEnSitio.MedioPago.class, d.getString("medioPago")));
        }
        // Con el validador activo esto no ocurre. Si ocurre, el documento entró sin validación.
        throw new IllegalStateException("tickets.detalle sin campo que identifique su módulo. Campos: " + d.keySet());
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor) {
        return valor == null ? null : Enum.valueOf(tipo, valor);
    }

    private static Double doubleDe(Object valor) {
        return valor instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer enteroDe(Object valor) {
        return valor instanceof Number n ? n.intValue() : null;
    }

    private static BigDecimal decimalDe(Object valor) {
        if (valor instanceof Decimal128 d) {
            return d.bigDecimalValue();
        }
        if (valor instanceof BigDecimal b) {
            return b;
        }
        return null;
    }
}
