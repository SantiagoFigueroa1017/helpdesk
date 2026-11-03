package co.edu.ufpso.helpdesk.tickets.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.test.context.ActiveProfiles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleCompras;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleInventarios;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleProduccion;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasEnSitio;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasOnline;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;

/**
 * Guarda y lee tickets contra el validador real de la colección.
 *
 * <p>Antes de cada caso copia el validador de {@code helpdesk.tickets} (replica set local) a
 * {@code helpdesk_test.tickets} con {@link ValidadoresDePrueba}. No toca los datos de
 * {@code helpdesk}.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketPersistenciaTest {

    private static final Instant CREACION = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private TicketRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private MappingMongoConverter converter;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidadorDeHelpdesk() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "tickets");
    }

    @Test
    void guardaUnIncidenteDeComprasComoLoExigeElValidador() {
        DetalleCompras detalle = new DetalleCompras("900123456-1", DetalleCompras.Etapa.CUENTA_POR_PAGAR,
                "OC-2026-00412", null, new BigDecimal("1250000.50"));
        Ticket guardado = repositorio.save(incidente(ModuloErp.COMPRAS, detalle));

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        Document detalleCrudo = crudo.get("detalle", Document.class);

        assertThat(crudo).doesNotContainKey("_class");
        assertThat(detalleCrudo).doesNotContainKey("_class");
        assertThat(detalleCrudo.get("montoInvolucrado")).isInstanceOf(Decimal128.class);
        // Opcional en null: no se escribe.
        assertThat(detalleCrudo).doesNotContainKey("numeroFacturaProveedor");
        // Obligatorios que pueden ser null: se escriben en null.
        assertThat(crudo).containsEntry("agenteId", null)
                .containsEntry("fechaResolucion", null)
                .containsEntry("fechaPrimeraAsignacion", null);
        assertThat(crudo.get("schemaVersion")).isEqualTo(1);

        Ticket leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getDetalle()).isEqualTo(detalle);
        assertThat(leido.detalleCoherente()).isTrue();
        assertThat(leido.getFechaCreacion()).isEqualTo(CREACION);
    }

    @Test
    void guardaUnaSugerenciaSinDetalle() {
        Ticket sugerencia = base(ModuloErp.INVENTARIOS, TipoTicket.SUGERENCIA);
        Ticket guardado = repositorio.save(sugerencia);

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("detalle");

        Ticket leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getDetalle()).isNull();
        assertThat(leido.getHistorialEstados()).containsExactlyElementsOf(sugerencia.getHistorialEstados());
    }

    @Test
    void leeLosCincoDetallesInsertadosComoLosInsertaMongosh() {
        // Mismos tipos que la semilla: int32 en existenciaSistema, double en temperaturaSensor,
        // Decimal128 en montoInvolucrado y ningún _class.
        coleccion.insertMany(List.of(
                crudo("VENTAS_ONLINE", new Document("pasarelaPagoId", "WOMPI-482193")
                        .append("urlError", "https://tienda.lacteoselzulia.co/checkout/pago?orden=55120")
                        .append("navegador", "Chrome 141")),
                crudo("PRODUCCION", new Document("maquinariaId", "MEZ-02")
                        .append("codigoLote", "L202610-118")
                        .append("faseProceso", "MEZCLADO")
                        .append("temperaturaSensor", 78.5)),
                crudo("INVENTARIOS", new Document("codigoBodega", "BOD-02")
                        .append("codigoProducto", "SKU-03301")
                        .append("tipoOperacion", "AJUSTE")
                        .append("existenciaSistema", -12)),
                crudo("COMPRAS", new Document("nitProveedor", "860512780-4")
                        .append("etapa", "RECEPCION")
                        .append("montoInvolucrado", new Decimal128(new BigDecimal("4300000.00")))),
                crudo("VENTAS_EN_SITIO", new Document("cajaId", "CAJA-03")
                        .append("turnoId", "T-20261015-M")
                        .append("dispositivo", "DATAFONO")
                        .append("medioPago", "TARJETA"))));

        Map<ModuloErp, DetalleTicket> porModulo = repositorio.findAll().stream()
                .collect(Collectors.toMap(Ticket::getModulo, Ticket::getDetalle));

        assertThat(porModulo.get(ModuloErp.VENTAS_ONLINE)).isInstanceOfSatisfying(DetalleVentasOnline.class,
                d -> assertThat(d.logApi()).isNull());
        assertThat(porModulo.get(ModuloErp.PRODUCCION)).isInstanceOfSatisfying(DetalleProduccion.class,
                d -> assertThat(d.temperaturaSensor()).isEqualTo(78.5));
        assertThat(porModulo.get(ModuloErp.INVENTARIOS)).isInstanceOfSatisfying(DetalleInventarios.class,
                d -> assertThat(d.existenciaSistema()).isEqualTo(-12));
        assertThat(porModulo.get(ModuloErp.COMPRAS)).isInstanceOfSatisfying(DetalleCompras.class,
                d -> assertThat(d.montoInvolucrado()).isEqualTo(new BigDecimal("4300000.00")));
        assertThat(porModulo.get(ModuloErp.VENTAS_EN_SITIO)).isInstanceOfSatisfying(DetalleVentasEnSitio.class,
                d -> assertThat(d.medioPago()).isEqualTo(DetalleVentasEnSitio.MedioPago.TARJETA));
    }

    @Test
    void elValidadorRechazaUnDetalleDeOtroModulo() {
        Ticket ticket = incidente(ModuloErp.PRODUCCION,
                new DetalleVentasOnline("PAYU-7781", "https://tienda.cliente.co/checkout", "Chrome 141", null));

        assertThat(ticket.detalleCoherente()).isFalse();
        // Sin la revisión del servicio, MongoDB lo rechaza (código 121) y el manejador responde
        // 500 ESQUEMA_RECHAZADO.
        assertThatThrownBy(() -> repositorio.save(ticket)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leeLosTicketsDeLaSemillaSiEstanCargadosEnElLocal() {
        long total = mongoClient.getDatabase("helpdesk").getCollection("tickets").countDocuments();
        assumeTrue(total > 0, "La semilla no está cargada en el local (pendiente de 1.5)");

        MongoTemplate helpdesk = new MongoTemplate(
                new SimpleMongoClientDatabaseFactory(mongoClient, "helpdesk"), converter);
        List<Ticket> tickets = helpdesk.findAll(Ticket.class);

        assertThat(tickets).hasSize((int) total).allMatch(Ticket::detalleCoherente);
        Map<ModuloErp, Long> incidentesPorModulo = tickets.stream()
                .filter(t -> t.getTipo() == TipoTicket.INCIDENTE)
                .collect(Collectors.groupingBy(Ticket::getModulo, Collectors.counting()));
        assertThat(incidentesPorModulo).containsOnlyKeys(ModuloErp.values());
    }

    // ---------- Datos de prueba ----------

    private static Ticket incidente(ModuloErp modulo, DetalleTicket detalle) {
        Ticket t = base(modulo, TipoTicket.INCIDENTE);
        t.setDetalle(detalle);
        return t;
    }

    private static Ticket base(ModuloErp modulo, TipoTicket tipo) {
        ObjectId cliente = new ObjectId();
        Ticket t = new Ticket();
        t.setFechaCreacion(CREACION);
        t.setFechaActualizacion(CREACION);
        t.setEmpresaId(new ObjectId());
        t.setCreadorId(cliente);
        t.setSolicitanteId(cliente);
        t.setModulo(modulo);
        t.setTipo(tipo);
        t.setTitulo("Prueba de persistencia");
        t.setDescripcion("Ticket creado por TicketPersistenciaTest");
        t.setEstado(EstadoTicket.ABIERTO);
        t.getHistorialEstados().add(new CambioEstado(EstadoTicket.ABIERTO, CREACION, cliente));
        t.setFechaLimiteRespuesta(CREACION.plusSeconds(24 * 3600));
        return t;
    }

    /** Documento armado a mano, como lo inserta la semilla desde mongosh. */
    private static Document crudo(String modulo, Document detalle) {
        ObjectId cliente = new ObjectId();
        Date creacion = Date.from(CREACION);
        return new Document("schemaVersion", 1)
                .append("fechaCreacion", creacion)
                .append("fechaActualizacion", creacion)
                .append("empresaId", new ObjectId())
                .append("creadorId", cliente)
                .append("solicitanteId", cliente)
                .append("agenteId", null)
                .append("modulo", modulo)
                .append("tipo", "INCIDENTE")
                .append("titulo", "Incidente de " + modulo)
                .append("descripcion", "Documento insertado sin pasar por Spring Data")
                .append("detalle", detalle)
                .append("estado", "ABIERTO")
                .append("historialEstados", List.of(new Document("estado", "ABIERTO")
                        .append("fecha", creacion).append("usuarioId", cliente)))
                .append("fechaResolucion", null)
                .append("fechaLimiteRespuesta", Date.from(CREACION.plusSeconds(24 * 3600)))
                .append("fechaPrimeraAsignacion", null);
    }
}
