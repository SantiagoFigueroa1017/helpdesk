package co.edu.ufpso.helpdesk.tickets.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.tickets.domain.CampoDetalle.Opcion;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleCompras.Etapa;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleInventarios.TipoOperacion;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleProduccion.FaseProceso;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasEnSitio.Dispositivo;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasEnSitio.MedioPago;

/**
 * Campos del {@code detalle} de cada módulo del ERP (HU21, modelo 1.2 sección 7, validador
 * {@code database/validadores/07_tickets.js}). Decidido en 2.6: el backend sirve esta definición en
 * {@code GET /api/tickets/campos} para el formulario dinámico, y la misma definición valida el
 * {@code detalle} que llega al crear o editar.
 *
 * <p>Los nombres y el orden de los campos son los de los componentes de cada record
 * ({@link DetalleVentasOnline}, {@link DetalleProduccion}...), y los valores de las opciones son
 * las constantes de sus enums. {@code CatalogoDetalleTest} comprueba las dos cosas, así que un campo
 * nuevo obliga a cambiar el record, este catálogo y el validador de MongoDB.
 */
public final class CatalogoDetalle {

    /** Longitud de los códigos e ids externos en el validador (1.3). */
    private static final int CODIGO = 50;

    /**
     * Lo que el catálogo sabe de un módulo.
     *
     * @param modulo   módulo del ERP
     * @param etiqueta nombre que muestra el formulario
     * @param clase    record del detalle de los incidentes de este módulo
     * @param campos   campos del detalle, en el orden de los componentes del record
     */
    public record DefinicionModulo(ModuloErp modulo, String etiqueta, Class<? extends DetalleTicket> clase,
            List<CampoDetalle> campos) {

        public DefinicionModulo {
            campos = List.copyOf(campos);
        }

        /** El campo con ese nombre, o null si el módulo no lo tiene. */
        public CampoDetalle campo(String nombre) {
            return campos.stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElse(null);
        }
    }

    private static final Map<ModuloErp, DefinicionModulo> DEFINICIONES = new EnumMap<>(ModuloErp.class);

    static {
        agregar(ModuloErp.VENTAS_ONLINE, "Ventas Online", DetalleVentasOnline.class, List.of(
                texto("pasarelaPagoId", "ID de la pasarela de pago", true, CODIGO),
                texto("urlError", "URL del error", true, 2048),
                texto("navegador", "Navegador web", true, 100),
                textoLargo("logApi", "Log de la API", false, 20000)));

        agregar(ModuloErp.INVENTARIOS, "Manejo de Inventarios", DetalleInventarios.class, List.of(
                texto("codigoBodega", "Código de bodega", true, CODIGO),
                texto("codigoProducto", "Código del producto", true, CODIGO),
                opcion("tipoOperacion", "Tipo de operación", true, List.of(
                        new Opcion(TipoOperacion.ENTRADA.name(), "Entrada"),
                        new Opcion(TipoOperacion.SALIDA.name(), "Salida"),
                        new Opcion(TipoOperacion.TRASLADO.name(), "Traslado"),
                        new Opcion(TipoOperacion.AJUSTE.name(), "Ajuste"),
                        new Opcion(TipoOperacion.ALERTA_STOCK.name(), "Alerta de stock"))),
                texto("codigoBarras", "Código de barras", false, CODIGO),
                simple("existenciaSistema", "Existencia en el sistema", TipoCampo.ENTERO)));

        agregar(ModuloErp.COMPRAS, "Compras", DetalleCompras.class, List.of(
                texto("nitProveedor", "NIT del proveedor", true, CODIGO),
                opcion("etapa", "Etapa", true, List.of(
                        new Opcion(Etapa.ORDEN_COMPRA.name(), "Orden de compra"),
                        new Opcion(Etapa.RECEPCION.name(), "Recepción"),
                        new Opcion(Etapa.CUENTA_POR_PAGAR.name(), "Cuenta por pagar"),
                        new Opcion(Etapa.PAGO_PROVEEDOR.name(), "Pago al proveedor"))),
                texto("numeroOrdenCompra", "Número de orden de compra", false, CODIGO),
                texto("numeroFacturaProveedor", "Número de factura del proveedor", false, CODIGO),
                simple("montoInvolucrado", "Monto involucrado", TipoCampo.DINERO)));

        agregar(ModuloErp.PRODUCCION, "Producción", DetalleProduccion.class, List.of(
                texto("maquinariaId", "ID de la maquinaria", true, CODIGO),
                texto("codigoLote", "Código de lote", true, CODIGO),
                opcion("faseProceso", "Fase del proceso", true, List.of(
                        new Opcion(FaseProceso.PREPARACION.name(), "Preparación"),
                        new Opcion(FaseProceso.MEZCLADO.name(), "Mezclado"),
                        new Opcion(FaseProceso.TRANSFORMACION.name(), "Transformación"),
                        new Opcion(FaseProceso.CONTROL_CALIDAD.name(), "Control de calidad"),
                        new Opcion(FaseProceso.EMPAQUE.name(), "Empaque"),
                        new Opcion(FaseProceso.ALMACENAMIENTO.name(), "Almacenamiento"))),
                simple("temperaturaSensor", "Temperatura del sensor (°C)", TipoCampo.DECIMAL)));

        agregar(ModuloErp.VENTAS_EN_SITIO, "Ventas en Sitio (Caja/POS)", DetalleVentasEnSitio.class, List.of(
                texto("cajaId", "ID de la caja", true, CODIGO),
                texto("turnoId", "ID del turno", true, CODIGO),
                opcion("dispositivo", "Dispositivo", true, List.of(
                        new Opcion(Dispositivo.IMPRESORA.name(), "Impresora"),
                        new Opcion(Dispositivo.LECTOR_CODIGO.name(), "Lector de código"),
                        new Opcion(Dispositivo.CAJON_MONEDERO.name(), "Cajón monedero"),
                        new Opcion(Dispositivo.DATAFONO.name(), "Datáfono"),
                        new Opcion(Dispositivo.NINGUNO.name(), "Ninguno"))),
                texto("numeroFactura", "Número de factura", false, CODIGO),
                opcion("medioPago", "Medio de pago", false, List.of(
                        new Opcion(MedioPago.EFECTIVO.name(), "Efectivo"),
                        new Opcion(MedioPago.TARJETA.name(), "Tarjeta"),
                        new Opcion(MedioPago.TRANSFERENCIA.name(), "Transferencia"),
                        new Opcion(MedioPago.MIXTO.name(), "Mixto")))));
    }

    private CatalogoDetalle() {
    }

    /** La definición del módulo. Existe para los cinco. */
    public static DefinicionModulo de(ModuloErp modulo) {
        return DEFINICIONES.get(modulo);
    }

    /** Los cinco módulos, en el orden del enum. */
    public static List<DefinicionModulo> todos() {
        return new ArrayList<>(DEFINICIONES.values());
    }

    // ---------- piezas ----------

    private static void agregar(ModuloErp modulo, String etiqueta, Class<? extends DetalleTicket> clase,
            List<CampoDetalle> campos) {
        DEFINICIONES.put(modulo, new DefinicionModulo(modulo, etiqueta, clase, campos));
    }

    private static CampoDetalle texto(String nombre, String etiqueta, boolean obligatorio, int maximo) {
        return new CampoDetalle(nombre, etiqueta, TipoCampo.TEXTO, obligatorio, maximo, List.of());
    }

    private static CampoDetalle textoLargo(String nombre, String etiqueta, boolean obligatorio, int maximo) {
        return new CampoDetalle(nombre, etiqueta, TipoCampo.TEXTO_LARGO, obligatorio, maximo, List.of());
    }

    private static CampoDetalle opcion(String nombre, String etiqueta, boolean obligatorio, List<Opcion> opciones) {
        return new CampoDetalle(nombre, etiqueta, TipoCampo.OPCION, obligatorio, null, opciones);
    }

    /** ENTERO, DECIMAL o DINERO. En el modelo los tres números del detalle son opcionales. */
    private static CampoDetalle simple(String nombre, String etiqueta, TipoCampo tipo) {
        return new CampoDetalle(nombre, etiqueta, tipo, false, null, List.of());
    }
}
