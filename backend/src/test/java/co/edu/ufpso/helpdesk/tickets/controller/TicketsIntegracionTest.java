package co.edu.ufpso.helpdesk.tickets.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.empresas.domain.Contacto;
import co.edu.ufpso.helpdesk.empresas.domain.Empresa;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.planes.domain.LimitesPlan;
import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import co.edu.ufpso.helpdesk.planes.domain.Plan;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Tickets (HU21 a HU24) de punta a punta, con token real y los validadores reales del {@code rs0}
 * local (tarea 2.6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketsIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final int HORAS_RESPUESTA = 48;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private JwtEncoder encoder;

    private Instant ahora;
    private Empresa ferreteria;
    private Empresa panaderia;
    private Plan plan;
    private Membresia membresiaPanaderia;
    private Usuario agente;
    private Usuario clienteFerreteria;
    private Usuario otroClienteFerreteria;
    private Usuario clientePanaderia;
    private Usuario soloConsulta;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "planes", "membresias", "tickets",
                "comentariosTicket", "articulos")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ferreteria = empresa("900123456-1", "Ferretería El Tornillo");
        panaderia = empresa("800765432-9", "Panadería La Espiga");
        plan = plan();
        membresia(ferreteria.getId(), EstadoMembresia.ACTIVA, ModuloErp.INVENTARIOS, ModuloErp.COMPRAS,
                ModuloErp.PRODUCCION);
        membresiaPanaderia = membresia(panaderia.getId(), EstadoMembresia.ACTIVA, ModuloErp.VENTAS_EN_SITIO);

        Rol rolAgente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.TICKETS_CREAR, Permiso.TICKETS_VER,
                Permiso.TICKETS_EDITAR, Permiso.TICKETS_ASIGNAR, Permiso.COMENTARIOS_CREAR));
        Rol rolCliente = rol("CLIENTE", AlcanceRol.EMPRESA, List.of(Permiso.TICKETS_CREAR,
                Permiso.TICKETS_VER_PROPIOS, Permiso.TICKETS_EDITAR_PROPIOS, Permiso.COMENTARIOS_CREAR));
        Rol rolConsulta = rol("SOLO_MEMBRESIA", AlcanceRol.EMPRESA, List.of(Permiso.MEMBRESIAS_VER));

        agente = usuario("Andrés Pabón", "andres@sistemasintegrados.com.co", null, rolAgente, true);
        clienteFerreteria = usuario("Camilo Quintero", "camilo@ferreteria.co", ferreteria.getId(), rolCliente, true);
        otroClienteFerreteria = usuario("Diana Rincón", "diana@ferreteria.co", ferreteria.getId(), rolCliente, true);
        clientePanaderia = usuario("Rosa Jaimes", "rosa@panaderia.co", panaderia.getId(), rolCliente, true);
        soloConsulta = usuario("Óscar Lobo", "oscar@ferreteria.co", ferreteria.getId(), rolConsulta, true);
    }

    // ---------- utilidades ----------

    private Empresa empresa(String nit, String razonSocial) {
        Empresa e = new Empresa();
        e.setFechaCreacion(ahora);
        e.setFechaActualizacion(ahora);
        e.setNit(nit);
        e.setRazonSocial(razonSocial);
        e.setContacto(new Contacto("Contacto", "contacto@empresa.co", "3001234567", null, null));
        return template.insert(e);
    }

    private Plan plan() {
        Plan p = new Plan();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setNombre("Estándar");
        p.setDescripcion("Plan de prueba");
        p.setPrecio(new BigDecimal("250000"));
        p.setNivelAcceso(NivelAcceso.ESTANDAR);
        p.setLimites(new LimitesPlan(HORAS_RESPUESTA, 1, 10, 3));
        p.setActivo(true);
        return template.insert(p);
    }

    private Membresia membresia(ObjectId empresaId, EstadoMembresia estado, ModuloErp... modulos) {
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(empresaId);
        m.setPlanId(plan.getId());
        m.setModulosContratados(List.of(modulos));
        m.setFechaInicio(ahora.minus(30, ChronoUnit.DAYS));
        m.setFechaVencimiento(ahora.plus(30, ChronoUnit.DAYS));
        m.setEstado(estado);
        return template.insert(m);
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de TicketsIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(true);
        r.setEsSistema(false);
        return template.insert(r);
    }

    private Usuario usuario(String nombre, String email, ObjectId empresaId, Rol rol, boolean activo) {
        Usuario u = new Usuario();
        u.setFechaCreacion(ahora);
        u.setFechaActualizacion(ahora);
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(HASH);
        u.setRolIds(List.of(rol.getId()));
        u.setEmpresaId(empresaId);
        u.setActivo(activo);
        return template.insert(u);
    }

    private String token(Usuario u) {
        Instant t = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(u.getId().toHexString())
                .issuedAt(t).expiresAt(t.plusSeconds(900)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private MockHttpServletRequestBuilder como(Usuario u, MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(u));
    }

    private MockHttpServletRequestBuilder crearComo(Usuario u, String cuerpo) {
        return como(u, post("/api/tickets")).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static String incidenteInventarios(String solicitanteId) {
        return "{" + (solicitanteId == null ? "" : "\"solicitanteId\":\"" + solicitanteId + "\",")
                + "\"modulo\":\"INVENTARIOS\",\"tipo\":\"INCIDENTE\",\"titulo\":\"  Existencia negativa  \","
                + "\"descripcion\":\"La bodega 2 muestra -4 unidades.\",\"detalle\":{\"codigoBodega\":\"B-02\","
                + "\"codigoProducto\":\"TOR-3/8\",\"tipoOperacion\":\"AJUSTE\",\"existenciaSistema\":-4,"
                + "\"codigoBarras\":\"\"}}";
    }

    private Document crudo(String id) {
        return template.getDb().getCollection("tickets").find(Filters.eq("_id", new ObjectId(id))).first();
    }

    private static String id(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    /** Sugerencia insertada directo en la base, para controlar fechas, estado y agente. */
    private Ticket ticket(String titulo, Usuario solicitante, ModuloErp modulo, EstadoTicket estado, Usuario agenteAsignado,
            Instant creado) {
        Ticket t = new Ticket();
        t.setFechaCreacion(creado);
        t.setFechaActualizacion(creado);
        t.setEmpresaId(solicitante.getEmpresaId());
        t.setCreadorId(solicitante.getId());
        t.setSolicitanteId(solicitante.getId());
        t.setAgenteId(agenteAsignado == null ? null : agenteAsignado.getId());
        t.setModulo(modulo);
        t.setTipo(TipoTicket.SUGERENCIA);
        t.setTitulo(titulo);
        t.setDescripcion("Descripción de " + titulo);
        t.setEstado(estado);
        t.setHistorialEstados(new ArrayList<>(List.of(new CambioEstado(EstadoTicket.ABIERTO, creado, solicitante.getId()))));
        t.setFechaResolucion(null);
        t.setFechaPrimeraAsignacion(agenteAsignado == null ? null : creado);
        t.setFechaLimiteRespuesta(creado.plus(Duration.ofHours(HORAS_RESPUESTA)));
        return template.insert(t);
    }

    /** Cuatro tickets: dos de Camilo, uno de Diana (Ferretería) y uno de Rosa (Panadería). */
    private void cuatroTickets() {
        ticket("Camilo 1", clienteFerreteria, ModuloErp.INVENTARIOS, EstadoTicket.ABIERTO, null, ahora.minus(3, ChronoUnit.HOURS));
        ticket("Camilo 2", clienteFerreteria, ModuloErp.COMPRAS, EstadoTicket.EN_PROCESO, agente, ahora.minus(2, ChronoUnit.HOURS));
        ticket("Diana 1", otroClienteFerreteria, ModuloErp.INVENTARIOS, EstadoTicket.ABIERTO, null, ahora.minus(1, ChronoUnit.HOURS));
        ticket("Rosa 1", clientePanaderia, ModuloErp.VENTAS_EN_SITIO, EstadoTicket.RESUELTO, agente, ahora);
    }

    private Usuario clienteAdminFerreteria() {
        Rol rolClienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA, List.of(Permiso.TICKETS_CREAR, Permiso.TICKETS_VER,
                Permiso.TICKETS_EDITAR_PROPIOS, Permiso.COMENTARIOS_CREAR));
        return usuario("Gloria Peña", "gloria@ferreteria.co", ferreteria.getId(), rolClienteAdmin, true);
    }

    // ---------- formulario dinámico ----------

    @Test
    void losCamposDelFormularioSalenDelCatalogo() throws Exception {
        mvc.perform(como(clienteFerreteria, get("/api/tickets/campos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].modulo").value("VENTAS_ONLINE"))
                .andExpect(jsonPath("$[0].campos[*].nombre").value(containsInAnyOrder(
                        "pasarelaPagoId", "urlError", "navegador", "logApi")))
                .andExpect(jsonPath("$[0].campos[0].obligatorio").value(true))
                .andExpect(jsonPath("$[0].campos[0].longitudMaxima").value(50))
                .andExpect(jsonPath("$[3].modulo").value("PRODUCCION"))
                .andExpect(jsonPath("$[3].campos[2].tipo").value("OPCION"))
                .andExpect(jsonPath("$[3].campos[2].opciones", hasSize(6)))
                .andExpect(jsonPath("$[3].campos[2].opciones[3].etiqueta").value("Control de calidad"));
    }

    @Test
    void losCamposExigenSesion() throws Exception {
        mvc.perform(get("/api/tickets/campos"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- HU21 · crear ----------

    @Test
    void elClienteCreaUnIncidenteYQuedaComoSolicitante() throws Exception {
        MvcResult resultado = mvc.perform(crearComo(clienteFerreteria, incidenteInventarios(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(ferreteria.getId().toHexString()))
                .andExpect(jsonPath("$.creadorId").value(clienteFerreteria.getId().toHexString()))
                .andExpect(jsonPath("$.solicitanteId").value(clienteFerreteria.getId().toHexString()))
                .andExpect(jsonPath("$.agenteId").doesNotExist())
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.titulo").value("Existencia negativa"))
                .andExpect(jsonPath("$.historialEstados", hasSize(1)))
                .andExpect(jsonPath("$.historialEstados[0].estado").value("ABIERTO"))
                .andExpect(jsonPath("$.detalle.existenciaSistema").value(-4))
                .andExpect(jsonPath("$.detalle.codigoBarras").doesNotExist())
                .andReturn();

        Document doc = crudo(id(resultado));
        Instant creado = doc.getDate("fechaCreacion").toInstant();
        assertThat(doc.getDate("fechaLimiteRespuesta").toInstant())
                .isEqualTo(creado.plus(Duration.ofHours(HORAS_RESPUESTA)));
        assertThat(doc).containsKeys("agenteId", "fechaResolucion", "fechaPrimeraAsignacion");
        assertThat(doc.get("agenteId")).isNull();
        Document detalle = doc.get("detalle", Document.class);
        assertThat(detalle).doesNotContainKey("codigoBarras");
        assertThat(detalle.get("existenciaSistema")).isInstanceOf(Integer.class);
        assertThat(doc).doesNotContainKey("_class");
    }

    @Test
    void losNumerosDelDetalleSeGuardanConElTipoDelValidador() throws Exception {
        String produccion = "{\"modulo\":\"PRODUCCION\",\"tipo\":\"INCIDENTE\",\"titulo\":\"Sensor\","
                + "\"descripcion\":\"Lectura alta\",\"detalle\":{\"maquinariaId\":\"MZ-04\",\"codigoLote\":\"L-118\","
                + "\"faseProceso\":\"MEZCLADO\",\"temperaturaSensor\":78}}";
        String compras = "{\"modulo\":\"COMPRAS\",\"tipo\":\"INCIDENTE\",\"titulo\":\"Factura\","
                + "\"descripcion\":\"No cuadra\",\"detalle\":{\"nitProveedor\":\"900111222-3\","
                + "\"etapa\":\"CUENTA_POR_PAGAR\",\"montoInvolucrado\":1250000.75}}";

        Document docProduccion = crudo(id(mvc.perform(crearComo(clienteFerreteria, produccion))
                .andExpect(status().isCreated()).andReturn()));
        Document docCompras = crudo(id(mvc.perform(crearComo(clienteFerreteria, compras))
                .andExpect(status().isCreated()).andReturn()));

        assertThat(docProduccion.get("detalle", Document.class).get("temperaturaSensor")).isEqualTo(78.0);
        assertThat(docCompras.get("detalle", Document.class).get("montoInvolucrado"))
                .isEqualTo(Decimal128.parse("1250000.75"));
    }

    @Test
    void unaSugerenciaSeGuardaSinDetalle() throws Exception {
        String sugerencia = "{\"modulo\":\"COMPRAS\",\"tipo\":\"SUGERENCIA\",\"titulo\":\"Aprobación en lote\","
                + "\"descripcion\":\"Aprobar varias órdenes a la vez.\",\"detalle\":{}}";

        MvcResult resultado = mvc.perform(crearComo(clienteFerreteria, sugerencia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SUGERENCIA"))
                .andExpect(jsonPath("$.detalle").doesNotExist())
                .andReturn();

        assertThat(crudo(id(resultado))).doesNotContainKey("detalle");
    }

    @Test
    void elDetalleMalFormadoResponde400ConCadaCampo() throws Exception {
        String malo = "{\"modulo\":\"INVENTARIOS\",\"tipo\":\"INCIDENTE\",\"titulo\":\"T\",\"descripcion\":\"D\","
                + "\"detalle\":{\"codigoBodega\":\"B-02\",\"tipoOperacion\":\"PERDIDA\",\"cajaId\":\"C1\"}}";
        String sugerenciaConDetalle = "{\"modulo\":\"COMPRAS\",\"tipo\":\"SUGERENCIA\",\"titulo\":\"T\","
                + "\"descripcion\":\"D\",\"detalle\":{\"etapa\":\"RECEPCION\"}}";

        mvc.perform(crearComo(clienteFerreteria, malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo").value(containsInAnyOrder(
                        "detalle.cajaId", "detalle.codigoProducto", "detalle.tipoOperacion")));
        mvc.perform(crearComo(clienteFerreteria, sugerenciaConDetalle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("detalle"));
        assertThat(template.getDb().getCollection("tickets").countDocuments()).isZero();
    }

    @Test
    void soloSeReportanModulosContratados() throws Exception {
        String ventasOnline = "{\"modulo\":\"VENTAS_ONLINE\",\"tipo\":\"SUGERENCIA\",\"titulo\":\"T\","
                + "\"descripcion\":\"D\"}";

        mvc.perform(crearComo(clienteFerreteria, ventasOnline))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("Ventas Online")));
    }

    @Test
    void sinSoporteNoSeCreaYSeDiceElMotivo() throws Exception {
        template.getDb().getCollection("membresias").updateOne(Filters.eq("_id", membresiaPanaderia.getId()),
                new Document("$set", new Document("estado", "SUSPENDIDA")));
        String caja = "{\"modulo\":\"VENTAS_EN_SITIO\",\"tipo\":\"SUGERENCIA\",\"titulo\":\"T\",\"descripcion\":\"D\"}";
        String cajaParaRosa = "{\"solicitanteId\":\"" + clientePanaderia.getId() + "\",\"modulo\":\"VENTAS_EN_SITIO\","
                + "\"tipo\":\"SUGERENCIA\",\"titulo\":\"T\",\"descripcion\":\"D\"}";

        mvc.perform(crearComo(clientePanaderia, caja))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOPORTE_BLOQUEADO"))
                .andExpect(jsonPath("$.motivo").value("SUSPENDIDA"));
        // El bloqueo también aplica al agente que crea en nombre del cliente (decidido en 2.5).
        mvc.perform(crearComo(agente, cajaParaRosa))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("SUSPENDIDA"));
    }

    @Test
    void unaMembresiaVencidaTambienBloquea() throws Exception {
        template.getDb().getCollection("membresias").updateOne(Filters.eq("_id", membresiaPanaderia.getId()),
                new Document("$set", new Document("fechaVencimiento", Date.from(ahora.minus(1, ChronoUnit.DAYS)))));

        mvc.perform(crearComo(clientePanaderia,
                        "{\"modulo\":\"VENTAS_EN_SITIO\",\"tipo\":\"SUGERENCIA\",\"titulo\":\"T\",\"descripcion\":\"D\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("VENCIDA"));
    }

    @Test
    void elAgenteCreaEnNombreDeUnCliente() throws Exception {
        mvc.perform(crearComo(agente, incidenteInventarios(clienteFerreteria.getId().toHexString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(ferreteria.getId().toHexString()))
                .andExpect(jsonPath("$.creadorId").value(agente.getId().toHexString()))
                .andExpect(jsonPath("$.solicitanteId").value(clienteFerreteria.getId().toHexString()))
                .andExpect(jsonPath("$.historialEstados[0].usuarioId").value(agente.getId().toHexString()));
    }

    @Test
    void elAgenteDebeIndicarUnSolicitanteValido() throws Exception {
        Usuario inactivo = usuario("Iván Ortiz", "ivan@ferreteria.co", ferreteria.getId(),
                template.findById(clienteFerreteria.getRolIds().get(0), Rol.class), false);

        mvc.perform(crearComo(agente, incidenteInventarios(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("solicitanteId"));
        mvc.perform(crearComo(agente, incidenteInventarios(agente.getId().toHexString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("empresa cliente")));
        mvc.perform(crearComo(agente, incidenteInventarios(inactivo.getId().toHexString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("inactivo")));
        mvc.perform(crearComo(agente, incidenteInventarios(new ObjectId().toHexString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("no existe")));
    }

    @Test
    void unClienteNoCreaEnNombreDeOtro() throws Exception {
        mvc.perform(crearComo(clienteFerreteria, incidenteInventarios(otroClienteFerreteria.getId().toHexString())))
                .andExpect(status().isUnprocessableContent());
        // Indicarse a sí mismo es lo mismo que dejarlo vacío.
        mvc.perform(crearComo(clienteFerreteria, incidenteInventarios(clienteFerreteria.getId().toHexString())))
                .andExpect(status().isCreated());
    }

    @Test
    void crearExigeElPermisoYLosCamposComunes() throws Exception {
        mvc.perform(crearComo(soloConsulta, incidenteInventarios(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(crearComo(clienteFerreteria, "{\"titulo\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("modulo")))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("tipo")))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("titulo")))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("descripcion")));
    }

    // ---------- HU22 · consultar ----------

    @Test
    void elClienteSoloVeLosTicketsEnLosQueEsSolicitante() throws Exception {
        cuatroTickets();
        Ticket deDiana = template.findAll(Ticket.class).stream().filter(t -> t.getTitulo().equals("Diana 1")).findFirst()
                .orElseThrow();

        mvc.perform(como(clienteFerreteria, get("/api/tickets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                // Orden por defecto: los más recientes primero.
                .andExpect(jsonPath("$.contenido[0].titulo").value("Camilo 2"))
                .andExpect(jsonPath("$.contenido[1].titulo").value("Camilo 1"));
        mvc.perform(como(clienteFerreteria, get("/api/tickets/" + deDiana.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void elClienteVeLosQueUnAgenteCreoASuNombre() throws Exception {
        String id = id(mvc.perform(crearComo(agente, incidenteInventarios(clienteFerreteria.getId().toHexString())))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(como(clienteFerreteria, get("/api/tickets")))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id));
        mvc.perform(como(clienteFerreteria, get("/api/tickets/" + id)))
                .andExpect(status().isOk());
    }

    @Test
    void elClienteAdminVeTodosLosDeSuEmpresaYSoloEsos() throws Exception {
        Usuario gloria = clienteAdminFerreteria();
        cuatroTickets();
        Ticket deRosa = template.findAll(Ticket.class).stream().filter(t -> t.getTitulo().equals("Rosa 1")).findFirst()
                .orElseThrow();

        mvc.perform(como(gloria, get("/api/tickets")))
                .andExpect(jsonPath("$.totalElementos").value(3));
        // El empresaId de la URL no cambia su alcance.
        mvc.perform(como(gloria, get("/api/tickets").param("empresaId", panaderia.getId().toHexString())))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mvc.perform(como(gloria, get("/api/tickets/" + deRosa.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAgenteVeTodosYFiltra() throws Exception {
        cuatroTickets();

        mvc.perform(como(agente, get("/api/tickets")))
                .andExpect(jsonPath("$.totalElementos").value(4));
        mvc.perform(como(agente, get("/api/tickets").param("empresaId", panaderia.getId().toHexString())))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].empresaNombre").value("Panadería La Espiga"));
        mvc.perform(como(agente, get("/api/tickets").param("sinAsignar", "true")))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(como(agente, get("/api/tickets").param("agenteId", agente.getId().toHexString())))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].agenteNombre").value("Andrés Pabón"));
        mvc.perform(como(agente, get("/api/tickets").param("estado", "ABIERTO").param("modulo", "INVENTARIOS")))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(como(agente, get("/api/tickets").param("tipo", "INCIDENTE")))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(como(agente, get("/api/tickets").param("agenteId", agente.getId().toHexString())
                        .param("sinAsignar", "true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        mvc.perform(como(agente, get("/api/tickets").param("estado", "PERDIDO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elRangoDeFechasUsaDiasCompletosEnHoraDeBogota() throws Exception {
        // Bogotá es UTC-5: 04:00Z del 1 de octubre todavía es 30 de septiembre allá.
        ticket("30 sep", clienteFerreteria, ModuloErp.COMPRAS, EstadoTicket.ABIERTO, null, Instant.parse("2026-10-01T04:00:00Z"));
        ticket("1 oct", clienteFerreteria, ModuloErp.COMPRAS, EstadoTicket.ABIERTO, null, Instant.parse("2026-10-01T06:00:00Z"));
        ticket("31 oct", clienteFerreteria, ModuloErp.COMPRAS, EstadoTicket.ABIERTO, null, Instant.parse("2026-11-01T04:30:00Z"));
        ticket("1 nov", clienteFerreteria, ModuloErp.COMPRAS, EstadoTicket.ABIERTO, null, Instant.parse("2026-11-01T05:30:00Z"));

        mvc.perform(como(agente, get("/api/tickets").param("desde", "2026-10-01").param("hasta", "2026-10-31")))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].titulo").value(containsInAnyOrder("1 oct", "31 oct")));
        mvc.perform(como(agente, get("/api/tickets").param("desde", "2026-11-01")))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(como(agente, get("/api/tickets").param("desde", "2026-10-31").param("hasta", "2026-10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void elListadoSeOrdenaPorFechaLimiteYSoloPorCamposConIndice() throws Exception {
        cuatroTickets();

        mvc.perform(como(agente, get("/api/tickets").param("estado", "ABIERTO")
                        .param("orden", "fechaLimiteRespuesta,asc")))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Camilo 1"))
                .andExpect(jsonPath("$.contenido[1].titulo").value("Diana 1"));
        mvc.perform(como(agente, get("/api/tickets").param("orden", "titulo,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void elListadoTraeElResumenConNombresYSinDetalle() throws Exception {
        mvc.perform(crearComo(clienteFerreteria, incidenteInventarios(null))).andExpect(status().isCreated());

        mvc.perform(como(clienteFerreteria, get("/api/tickets")))
                .andExpect(jsonPath("$.contenido[0].solicitanteNombre").value("Camilo Quintero"))
                .andExpect(jsonPath("$.contenido[0].empresaNombre").value("Ferretería El Tornillo"))
                .andExpect(jsonPath("$.contenido[0].fechaLimiteRespuesta").exists())
                .andExpect(jsonPath("$.contenido[0].detalle").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].descripcion").doesNotExist());
    }

    @Test
    void elDetalleTraeLosCamposDelModuloYLosNombres() throws Exception {
        Usuario otroAgente = usuario("Paula Ardila", "paula@sistemasintegrados.com.co", null,
                template.findById(agente.getRolIds().get(0), Rol.class), true);
        String id = id(mvc.perform(crearComo(otroAgente, incidenteInventarios(clienteFerreteria.getId().toHexString())))
                .andExpect(status().isCreated()).andReturn());
        template.remove(otroAgente);

        mvc.perform(como(clienteFerreteria, get("/api/tickets/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("La bodega 2 muestra -4 unidades."))
                .andExpect(jsonPath("$.detalle.codigoBodega").value("B-02"))
                .andExpect(jsonPath("$.detalle.tipoOperacion").value("AJUSTE"))
                .andExpect(jsonPath("$.solicitanteNombre").value("Camilo Quintero"))
                // Quien lo creó ya no existe (modelo 1.2, referencias sin documento).
                .andExpect(jsonPath("$.creadorNombre").value("Usuario eliminado"))
                .andExpect(jsonPath("$.historialEstados[0].usuarioNombre").value("Usuario eliminado"))
                .andExpect(jsonPath("$.agenteNombre").doesNotExist());
    }

    @Test
    void sinSoporteElClienteSigueViendoSusTickets() throws Exception {
        cuatroTickets();
        template.getDb().getCollection("membresias").updateOne(Filters.eq("_id", membresiaPanaderia.getId()),
                new Document("$set", new Document("estado", "SUSPENDIDA")));
        Ticket deRosa = template.findAll(Ticket.class).stream().filter(t -> t.getTitulo().equals("Rosa 1")).findFirst()
                .orElseThrow();

        mvc.perform(como(clientePanaderia, get("/api/tickets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(como(clientePanaderia, get("/api/tickets/" + deRosa.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void consultarExigePermisoYUnIdValido() throws Exception {
        mvc.perform(como(soloConsulta, get("/api/tickets")))
                .andExpect(status().isForbidden());
        mvc.perform(como(agente, get("/api/tickets/no-es-un-id")))
                .andExpect(status().isNotFound());
        mvc.perform(como(agente, get("/api/tickets/" + new ObjectId())))
                .andExpect(status().isNotFound());
    }
}
