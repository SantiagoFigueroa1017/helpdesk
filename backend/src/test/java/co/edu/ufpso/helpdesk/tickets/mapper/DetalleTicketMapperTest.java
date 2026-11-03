package co.edu.ufpso.helpdesk.tickets.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ErrorCampo;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleCompras;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleInventarios;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleProduccion;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasOnline;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;

/** Validación y conversión del {@code detalle} polimórfico (tarea 2.6), sin Spring ni MongoDB. */
class DetalleTicketMapperTest {

    @Test
    void armaElRecordDelModuloYConvierteLosTipos() {
        DetalleTicket detalle = DetalleTicketMapper.desdeSolicitud(ModuloErp.PRODUCCION, TipoTicket.INCIDENTE,
                Map.of("maquinariaId", " MZ-04 ", "codigoLote", "L-2026-118", "faseProceso", "MEZCLADO",
                        "temperaturaSensor", 78));

        assertThat(detalle).isEqualTo(new DetalleProduccion("MZ-04", "L-2026-118",
                DetalleProduccion.FaseProceso.MEZCLADO, 78.0));
    }

    @Test
    void losOpcionalesVaciosNoSeGuardan() {
        Map<String, Object> entrada = new HashMap<>();
        entrada.put("pasarelaPagoId", "PSE-77");
        entrada.put("urlError", "https://tienda.co/pago");
        entrada.put("navegador", "Firefox 141");
        entrada.put("logApi", "");

        DetalleVentasOnline detalle = (DetalleVentasOnline) DetalleTicketMapper.desdeSolicitud(
                ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, entrada);

        assertThat(detalle.logApi()).isNull();
    }

    @Test
    void enteroYDineroAceptanSoloValoresValidos() {
        DetalleInventarios inventario = (DetalleInventarios) DetalleTicketMapper.desdeSolicitud(
                ModuloErp.INVENTARIOS, TipoTicket.INCIDENTE, Map.of("codigoBodega", "B1", "codigoProducto", "P1",
                        "tipoOperacion", "AJUSTE", "existenciaSistema", 12.0));
        assertThat(inventario.existenciaSistema()).isEqualTo(12);

        DetalleCompras compra = (DetalleCompras) DetalleTicketMapper.desdeSolicitud(ModuloErp.COMPRAS,
                TipoTicket.INCIDENTE, Map.of("nitProveedor", "900111222-3", "etapa", "RECEPCION",
                        "montoInvolucrado", 1500000.50));
        assertThat(compra.montoInvolucrado()).isEqualByComparingTo(new BigDecimal("1500000.5"));

        assertThat(errores(ModuloErp.INVENTARIOS, Map.of("codigoBodega", "B1", "codigoProducto", "P1",
                "tipoOperacion", "AJUSTE", "existenciaSistema", 3.5)))
                .containsExactly(new ErrorCampo("detalle.existenciaSistema", "Debe ser un número entero."));
        assertThat(errores(ModuloErp.COMPRAS, Map.of("nitProveedor", "N", "etapa", "RECEPCION",
                "montoInvolucrado", 10.123)))
                .extracting(ErrorCampo::campo).containsExactly("detalle.montoInvolucrado");
        assertThat(errores(ModuloErp.COMPRAS, Map.of("nitProveedor", "N", "etapa", "RECEPCION",
                "montoInvolucrado", -5)))
                .containsExactly(new ErrorCampo("detalle.montoInvolucrado", "No puede ser negativo."));
    }

    @Test
    void reportaTodosLosErroresJuntos() {
        List<ErrorCampo> errores = errores(ModuloErp.PRODUCCION, Map.of("codigoLote", "x".repeat(51),
                "faseProceso", "HORNEADO", "temperaturaSensor", "caliente", "cajaId", "C1"));

        assertThat(errores).extracting(ErrorCampo::campo).containsExactlyInAnyOrder(
                "detalle.cajaId", "detalle.maquinariaId", "detalle.codigoLote", "detalle.faseProceso",
                "detalle.temperaturaSensor");
    }

    @Test
    void unIncidenteSinDetallePideLosObligatorios() {
        assertThat(errores(ModuloErp.COMPRAS, null)).extracting(ErrorCampo::campo)
                .containsExactly("detalle.nitProveedor", "detalle.etapa");
    }

    @Test
    void unaSugerenciaNoLlevaDetalle() {
        assertThat(DetalleTicketMapper.desdeSolicitud(ModuloErp.COMPRAS, TipoTicket.SUGERENCIA, null)).isNull();
        assertThat(DetalleTicketMapper.desdeSolicitud(ModuloErp.COMPRAS, TipoTicket.SUGERENCIA, Map.of())).isNull();
        assertThatThrownBy(() -> DetalleTicketMapper.desdeSolicitud(ModuloErp.COMPRAS, TipoTicket.SUGERENCIA,
                Map.of("etapa", "RECEPCION")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCodigo()).isEqualTo(CodigoError.VALIDACION);
    }

    @Test
    void laRespuestaTraeSoloLosCamposConValorYLosEnumsPorNombre() {
        Map<String, Object> mapa = DetalleTicketMapper.aRespuesta(
                new DetalleProduccion("MZ-04", "L-1", DetalleProduccion.FaseProceso.EMPAQUE, null));

        assertThat(mapa).containsExactly(Map.entry("maquinariaId", "MZ-04"), Map.entry("codigoLote", "L-1"),
                Map.entry("faseProceso", "EMPAQUE"));
        assertThat(DetalleTicketMapper.aRespuesta(null)).isNull();
    }

    @SuppressWarnings("unchecked")
    private static List<ErrorCampo> errores(ModuloErp modulo, Map<String, Object> entrada) {
        try {
            DetalleTicketMapper.desdeSolicitud(modulo, TipoTicket.INCIDENTE, entrada);
        } catch (ApiException e) {
            assertThat(e.getCodigo()).isEqualTo(CodigoError.VALIDACION);
            return (List<ErrorCampo>) e.getPropiedades().get("errores");
        }
        throw new AssertionError("Se esperaba un error de validación");
    }
}
