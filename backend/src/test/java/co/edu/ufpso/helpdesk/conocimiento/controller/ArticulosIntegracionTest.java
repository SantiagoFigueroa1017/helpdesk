package co.edu.ufpso.helpdesk.conocimiento.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.domain.Visibilidad;
import co.edu.ufpso.helpdesk.conocimiento.service.EmbeddingNoDisponibleException.Motivo;
import co.edu.ufpso.helpdesk.conocimiento.service.GeminiEmbeddings;
import co.edu.ufpso.helpdesk.conocimiento.service.PropiedadesGemini;
import co.edu.ufpso.helpdesk.conocimiento.service.ProveedorEmbeddings;
import co.edu.ufpso.helpdesk.conocimiento.service.RegeneracionEmbeddings;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ProveedorEmbeddingsFalso;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Base de conocimientos (HU29 a HU32) de punta a punta, con token real, los validadores y los índices reales
 * del {@code rs0} local y un proveedor de vectores falso en lugar de Gemini (tarea 2.7).
 *
 * <p>Requisitos en el local: {@code database/validadores/00_todos_los_validadores.js} y
 * {@code database/indices/00_indices.js} (con {@code ix_articulos_modulo_fecha}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticulosIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /** Gemini falso, compartido por todas las pruebas de esta clase. */
    @TestConfiguration
    static class ConProveedorFalso {

        @Bean
        @Primary
        ProveedorEmbeddingsFalso proveedorEmbeddingsFalso() {
            return new ProveedorEmbeddingsFalso();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private ProveedorEmbeddingsFalso gemini;

    private MongoCollection<Document> articulos;
    private Instant ahora;
    private Usuario agente;
    private Usuario cliente;
    private Ticket ticket;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "tickets", "articulos")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ValidadoresDePrueba.copiarIndices(mongoClient, template, "articulos", "txt_articulos",
                "ix_articulos_modulo_fecha", "ix_articulos_ticketIds");
        articulos = template.getDb().getCollection("articulos");
        gemini.reiniciar();
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Rol rolAgente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.ARTICULOS_VER,
                Permiso.ARTICULOS_VER_INTERNOS, Permiso.ARTICULOS_CREAR, Permiso.ARTICULOS_EDITAR,
                Permiso.ARTICULOS_ELIMINAR, Permiso.TICKETS_VER));
        Rol rolCliente = rol("CLIENTE", AlcanceRol.EMPRESA, List.of(Permiso.ARTICULOS_VER, Permiso.TICKETS_VER_PROPIOS));
        agente = usuario("Andrés Pabón", "andres@sistemasintegrados.com.co", null, rolAgente);
        cliente = usuario("Camilo Quintero", "camilo@ferreteria.co", new ObjectId(), rolCliente);
        ticket = ticket(cliente);
    }

    // ---------- utilidades ----------

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de ArticulosIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(true);
        r.setEsSistema(false);
        return template.insert(r);
    }

    private Usuario usuario(String nombre, String email, ObjectId empresaId, Rol rol) {
        Usuario u = new Usuario();
        u.setFechaCreacion(ahora);
        u.setFechaActualizacion(ahora);
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(HASH);
        u.setRolIds(List.of(rol.getId()));
        u.setEmpresaId(empresaId);
        u.setActivo(true);
        return template.insert(u);
    }

    private Ticket ticket(Usuario solicitante) {
        Ticket t = new Ticket();
        t.setFechaCreacion(ahora);
        t.setFechaActualizacion(ahora);
        t.setEmpresaId(solicitante.getEmpresaId());
        t.setCreadorId(solicitante.getId());
        t.setSolicitanteId(solicitante.getId());
        t.setAgenteId(null);
        t.setModulo(ModuloErp.VENTAS_EN_SITIO);
        t.setTipo(TipoTicket.SUGERENCIA);
        t.setTitulo("El datáfono se desconecta");
        t.setDescripcion("Pasa en la caja 2");
        t.setEstado(EstadoTicket.ABIERTO);
        t.setHistorialEstados(new ArrayList<>(List.of(new CambioEstado(EstadoTicket.ABIERTO, ahora, solicitante.getId()))));
        t.setFechaResolucion(null);
        t.setFechaPrimeraAsignacion(null);
        t.setFechaLimiteRespuesta(ahora.plus(Duration.ofHours(48)));
        return template.insert(t);
    }

    /** Artículo insertado directo en la base, con el vector pendiente salvo que se indique un modelo. */
    private Articulo articulo(String titulo, String contenido, ModuloErp modulo, Visibilidad visibilidad,
            Instant creado, String modelo) {
        Articulo a = new Articulo();
        a.setFechaCreacion(creado);
        a.setFechaActualizacion(creado);
        a.setTitulo(titulo);
        a.setContenido(contenido);
        a.setModulo(modulo);
        a.setEtiquetas(new ArrayList<>(List.of("prueba")));
        a.setVisibilidad(visibilidad);
        a.setTicketIds(new ArrayList<>());
        a.setAutorId(agente.getId());
        if (modelo != null) {
            a.asignarEmbedding(ProveedorEmbeddingsFalso.vector(titulo), modelo);
        }
        return template.insert(a);
    }

    private Articulo articulo(String titulo, ModuloErp modulo, Visibilidad visibilidad) {
        return articulo(titulo, "Pasos para resolver: " + titulo, modulo, visibilidad, ahora, null);
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

    private MockHttpServletRequestBuilder conCuerpo(Usuario u, MockHttpServletRequestBuilder peticion, String cuerpo) {
        return como(u, peticion).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static String cuerpo(String titulo, String contenido, String modulo, String extra) {
        return "{\"titulo\":\"" + titulo + "\",\"contenido\":\"" + contenido + "\",\"modulo\":\"" + modulo + "\""
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private Document crudo(String id) {
        return articulos.find(Filters.eq("_id", new ObjectId(id))).first();
    }

    private static String id(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    // ---------- HU29 · Registrar ----------

    @Test
    void crearGeneraElVectorYGuardaEtiquetasLimpias() throws Exception {
        MvcResult r = mvc.perform(conCuerpo(agente, post("/api/articulos"), cuerpo("  El datáfono no conecta  ",
                "Reinicie el turno de caja.", "VENTAS_EN_SITIO",
                "\"etiquetas\":[\" datafono \",\"caja\",\"datafono\"],\"ticketIds\":[\"" + ticket.getId() + "\"]")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/articulos/")))
                .andExpect(jsonPath("$.titulo").value("El datáfono no conecta"))
                .andExpect(jsonPath("$.etiquetas", contains("datafono", "caja")))
                .andExpect(jsonPath("$.visibilidad").value("INTERNO"))
                .andExpect(jsonPath("$.autorNombre").value("Andrés Pabón"))
                .andExpect(jsonPath("$.ticketsVinculados").value(1))
                .andExpect(jsonPath("$.ticketIds[0]").value(ticket.getId().toHexString()))
                .andExpect(jsonPath("$.vectorPendiente").value(false))
                .andExpect(jsonPath("$.embedding").doesNotExist())
                .andReturn();

        Document d = crudo(id(r));
        assertThat(d).doesNotContainKey("_class").containsEntry("embeddingModelo", GeminiEmbeddings.MODELO);
        assertThat(d.getList("embedding", Object.class)).hasSize(768).allMatch(v -> v instanceof Double);
        assertThat(gemini.documentos()).containsExactly("El datáfono no conecta\ndatafono, caja\nReinicie el turno de caja.");
    }

    @Test
    void siGeminiNoRespondeElArticuloSeGuardaConElVectorPendiente() throws Exception {
        gemini.fallarCon(Motivo.LIMITE_ALCANZADO);
        MvcResult r = mvc.perform(conCuerpo(agente, post("/api/articulos"),
                cuerpo("Bodega sin existencias", "Revise el ajuste.", "INVENTARIOS", "\"visibilidad\":\"PUBLICO\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vectorPendiente").value(true))
                .andExpect(jsonPath("$.visibilidad").value("PUBLICO"))
                .andReturn();

        Document d = crudo(id(r));
        assertThat(d).containsEntry("embedding", null).containsEntry("embeddingModelo", null);
    }

    @Test
    void unTicketInexistenteResponde422YNoGuardaNada() throws Exception {
        ObjectId inexistente = new ObjectId();
        mvc.perform(conCuerpo(agente, post("/api/articulos"), cuerpo("Título", "Contenido", "COMPRAS",
                "\"ticketIds\":[\"" + inexistente + "\"]")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detail", containsString(inexistente.toHexString())));
        assertThat(articulos.countDocuments()).isZero();
    }

    @Test
    void validaLosCamposYLosPermisos() throws Exception {
        String veintiUna = "\"etiquetas\":[" + String.join(",", Collections.nCopies(21, "\"x\"")) + "]";
        mvc.perform(conCuerpo(agente, post("/api/articulos"), cuerpo(" ", "Contenido", "COMPRAS", veintiUna)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("titulo")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("etiquetas")));

        mvc.perform(conCuerpo(cliente, post("/api/articulos"), cuerpo("Título", "Contenido", "COMPRAS", "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        assertThat(gemini.documentos()).isEmpty();
    }

    // ---------- HU30 · Consultar ----------

    @Test
    void elClienteSoloVeLosPublicos() throws Exception {
        Articulo publico = articulo("Caja lenta al facturar", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO);
        Articulo interno = articulo("Reiniciar el servicio de la caja", ModuloErp.VENTAS_EN_SITIO, Visibilidad.INTERNO);

        mvc.perform(como(cliente, get("/api/articulos?visibilidad=INTERNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(publico.getId().toHexString()));
        mvc.perform(como(cliente, get("/api/articulos/" + interno.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(como(cliente, get("/api/articulos/" + publico.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").value("Pasos para resolver: Caja lenta al facturar"));

        mvc.perform(como(agente, get("/api/articulos")))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(como(agente, get("/api/articulos?visibilidad=INTERNO")))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(interno.getId().toHexString()));
    }

    @Test
    void elListadoFiltraPorModuloYOrdenaPorFecha() throws Exception {
        Articulo viejo = articulo("Orden de compra duplicada", "Contenido", ModuloErp.COMPRAS, Visibilidad.PUBLICO,
                ahora.minus(3, ChronoUnit.DAYS), null);
        Articulo medio = articulo("Lote sin receta", "Contenido", ModuloErp.PRODUCCION, Visibilidad.PUBLICO,
                ahora.minus(2, ChronoUnit.DAYS), null);
        Articulo nuevo = articulo("Proveedor sin NIT", "Contenido", ModuloErp.COMPRAS, Visibilidad.PUBLICO,
                ahora.minus(1, ChronoUnit.DAYS), null);

        mvc.perform(como(agente, get("/api/articulos")))
                .andExpect(jsonPath("$.contenido[*].id", contains(nuevo.getId().toHexString(),
                        medio.getId().toHexString(), viejo.getId().toHexString())))
                .andExpect(jsonPath("$.contenido[0].contenido").doesNotExist());
        mvc.perform(como(agente, get("/api/articulos?modulo=COMPRAS&orden=fechaCreacion,asc")))
                .andExpect(jsonPath("$.contenido[*].id", contains(viejo.getId().toHexString(),
                        nuevo.getId().toHexString())));
        mvc.perform(como(agente, get("/api/articulos?orden=titulo,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void losIdsDeTicketsSoloLosVeElPersonalDeSoporte() throws Exception {
        Articulo a = articulo("Caja lenta al facturar", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO);
        template.updateFirst(new Query(Criteria.where("_id").is(a.getId())),
                new Update().push("ticketIds", ticket.getId()), Articulo.class);

        mvc.perform(como(agente, get("/api/articulos/" + a.getId())))
                .andExpect(jsonPath("$.ticketsVinculados").value(1))
                .andExpect(jsonPath("$.ticketIds[0]").value(ticket.getId().toHexString()));
        mvc.perform(como(cliente, get("/api/articulos/" + a.getId())))
                .andExpect(jsonPath("$.ticketsVinculados").value(1))
                .andExpect(jsonPath("$.ticketIds").value(nullValue()));
    }

    // ---------- HU31 · Actualizar ----------

    @Test
    void actualizarRegeneraElVectorSoloSiCambiaElTexto() throws Exception {
        Articulo a = articulo("Caja lenta", "Contenido viejo", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO,
                ahora.minus(1, ChronoUnit.DAYS), GeminiEmbeddings.MODELO);

        // Solo cambia el módulo: el texto del vector es el mismo.
        mvc.perform(conCuerpo(agente, put("/api/articulos/" + a.getId()), cuerpo("Caja lenta", "Contenido viejo",
                "VENTAS_ONLINE", "\"etiquetas\":[\"prueba\"],\"visibilidad\":\"PUBLICO\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulo").value("VENTAS_ONLINE"))
                .andExpect(jsonPath("$.vectorPendiente").value(false));
        assertThat(gemini.documentos()).isEmpty();
        assertThat(crudo(a.getId().toHexString()).getDate("fechaActualizacion").toInstant()).isAfter(a.getFechaActualizacion());

        mvc.perform(conCuerpo(agente, put("/api/articulos/" + a.getId()), cuerpo("Caja lenta", "Contenido nuevo",
                "VENTAS_ONLINE", "\"etiquetas\":[\"prueba\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").value("Contenido nuevo"))
                .andExpect(jsonPath("$.visibilidad").value("INTERNO"));
        assertThat(gemini.documentos()).containsExactly("Caja lenta\nprueba\nContenido nuevo");
        assertThat(crudo(a.getId().toHexString()).getList("embedding", Double.class))
                .isEqualTo(ProveedorEmbeddingsFalso.vector("Caja lenta\nprueba\nContenido nuevo"));
    }

    @Test
    void unVectorSinteticoDeLaSemillaSeRegeneraAlEditar() throws Exception {
        Articulo a = articulo("Caja lenta", "Contenido", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO, ahora,
                "sintetico/768");
        mvc.perform(como(agente, get("/api/articulos/" + a.getId())))
                .andExpect(jsonPath("$.vectorPendiente").value(true));

        mvc.perform(conCuerpo(agente, put("/api/articulos/" + a.getId()), cuerpo("Caja lenta", "Contenido",
                "VENTAS_EN_SITIO", "\"etiquetas\":[\"prueba\"],\"visibilidad\":\"PUBLICO\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorPendiente").value(false));
        assertThat(crudo(a.getId().toHexString())).containsEntry("embeddingModelo", GeminiEmbeddings.MODELO);
    }

    @Test
    void actualizarUnoQueNoExisteResponde404() throws Exception {
        mvc.perform(conCuerpo(agente, put("/api/articulos/" + new ObjectId()), cuerpo("T", "C", "COMPRAS", "")))
                .andExpect(status().isNotFound());
        mvc.perform(conCuerpo(agente, put("/api/articulos/no-es-un-id"), cuerpo("T", "C", "COMPRAS", "")))
                .andExpect(status().isNotFound());
    }

    // ---------- HU30 · Vincular como solución ----------

    @Test
    void vincularYDesvincularUnTicket() throws Exception {
        Articulo a = articulo("Caja lenta", "Contenido", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO,
                ahora.minus(1, ChronoUnit.DAYS), null);
        String ruta = "/api/articulos/" + a.getId() + "/tickets/" + ticket.getId();

        mvc.perform(como(agente, post(ruta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsVinculados").value(1));
        mvc.perform(como(agente, post(ruta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsVinculados").value(1));
        Document d = crudo(a.getId().toHexString());
        assertThat(d.getList("ticketIds", ObjectId.class)).containsExactly(ticket.getId());
        assertThat(d.getDate("fechaActualizacion")).isAfter(Date.from(a.getFechaActualizacion()));

        mvc.perform(como(agente, post("/api/articulos/" + a.getId() + "/tickets/" + new ObjectId())))
                .andExpect(status().isNotFound());
        mvc.perform(como(cliente, post(ruta)))
                .andExpect(status().isForbidden());

        mvc.perform(como(agente, delete(ruta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsVinculados").value(0));
    }

    @Test
    void conQuinientosTicketsNoSeVinculaOtro() throws Exception {
        Articulo a = articulo("Caja lenta", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO);
        List<ObjectId> quinientos = new ArrayList<>();
        for (int i = 0; i < Articulo.MAX_TICKETS; i++) {
            quinientos.add(new ObjectId());
        }
        template.updateFirst(new Query(Criteria.where("_id").is(a.getId())), new Update().set("ticketIds", quinientos),
                Articulo.class);

        mvc.perform(como(agente, post("/api/articulos/" + a.getId() + "/tickets/" + ticket.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("500")));
    }

    // ---------- HU32 · Eliminar ----------

    @Test
    void eliminarNoTocaLosTicketsVinculados() throws Exception {
        Articulo a = articulo("Caja lenta", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO);
        template.updateFirst(new Query(Criteria.where("_id").is(a.getId())),
                new Update().push("ticketIds", ticket.getId()), Articulo.class);

        mvc.perform(como(cliente, delete("/api/articulos/" + a.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(como(agente, delete("/api/articulos/" + a.getId())))
                .andExpect(status().isNoContent());
        mvc.perform(como(agente, delete("/api/articulos/" + a.getId())))
                .andExpect(status().isNotFound());

        assertThat(articulos.countDocuments()).isZero();
        assertThat(template.findById(ticket.getId(), Ticket.class)).isNotNull();
    }

    // ---------- HU30 · Búsqueda por palabras ----------

    @Test
    void laBusquedaPorTextoOrdenaPorRelevanciaYRespetaLaVisibilidad() throws Exception {
        Articulo enTitulo = articulo("El datáfono no conecta", "Revise la red de la caja.", ModuloErp.VENTAS_EN_SITIO,
                Visibilidad.PUBLICO, ahora, null);
        Articulo enContenido = articulo("Turno de caja sin cerrar", "Si el datáfono quedó con una venta abierta, "
                + "anúlela.", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO, ahora, null);
        Articulo interno = articulo("Datáfono: cambiar la llave", "Solo personal técnico.", ModuloErp.VENTAS_EN_SITIO,
                Visibilidad.INTERNO, ahora, null);
        articulo("Bodega con existencias negativas", "Haga un ajuste.", ModuloErp.INVENTARIOS, Visibilidad.PUBLICO,
                ahora, null);

        mvc.perform(como(cliente, get("/api/articulos/busqueda").param("texto", "datafono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("TEXTO"))
                .andExpect(jsonPath("$.aviso").value(nullValue()))
                .andExpect(jsonPath("$.resultados[*].id", contains(enTitulo.getId().toHexString(),
                        enContenido.getId().toHexString())))
                .andExpect(jsonPath("$.resultados[0].extracto").value("Revise la red de la caja."));

        mvc.perform(como(agente, get("/api/articulos/busqueda").param("texto", "datáfono")))
                .andExpect(jsonPath("$.resultados", hasSize(3)))
                .andExpect(jsonPath("$.resultados[*].id", hasItem(interno.getId().toHexString())));
        mvc.perform(como(agente, get("/api/articulos/busqueda").param("texto", "datáfono").param("modulo", "INVENTARIOS")))
                .andExpect(jsonPath("$.resultados", hasSize(0)));
        mvc.perform(como(agente, get("/api/articulos/busqueda").param("texto", "datáfono").param("limite", "1")))
                .andExpect(jsonPath("$.resultados", hasSize(1)));
        mvc.perform(como(agente, get("/api/articulos/busqueda").param("texto", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ---------- HU30 · Búsqueda por significado ----------

    @Test
    void enElLocalLaBusquedaPorSignificadoRespondeConLaDeTexto() throws Exception {
        articulo("El datáfono no conecta", ModuloErp.VENTAS_EN_SITIO, Visibilidad.PUBLICO);

        mvc.perform(como(cliente, get("/api/articulos/busqueda-semantica")
                .param("consulta", "el datáfono de la caja no funciona")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("TEXTO"))
                .andExpect(jsonPath("$.aviso").value("VECTORIAL_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.resultados", hasSize(1)));
        assertThat(gemini.consultas()).containsExactly("el datáfono de la caja no funciona");
    }

    @Test
    void laMismaConsultaNoVuelveAPedirElVector() throws Exception {
        String consulta = "no imprime la factura en la caja tres";
        mvc.perform(como(agente, get("/api/articulos/busqueda-semantica").param("consulta", consulta)))
                .andExpect(status().isOk());
        mvc.perform(como(agente, get("/api/articulos/busqueda-semantica").param("consulta", "  NO imprime la factura "
                + "en la   caja tres")))
                .andExpect(status().isOk());
        assertThat(gemini.consultas()).hasSize(1);
    }

    @Test
    void sinCuotaDeGeminiRespondeConLaDeTextoYLoAvisa() throws Exception {
        gemini.fallarCon(Motivo.LIMITE_ALCANZADO);
        mvc.perform(como(agente, get("/api/articulos/busqueda-semantica")
                .param("consulta", "la orden de compra quedó duplicada hoy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("TEXTO"))
                .andExpect(jsonPath("$.aviso").value("GEMINI_LIMITE_ALCANZADO"));

        gemini.fallarCon(Motivo.NO_CONFIGURADO);
        mvc.perform(como(agente, get("/api/articulos/busqueda-semantica")
                .param("consulta", "el lote no descuenta la materia prima")))
                .andExpect(jsonPath("$.aviso").value("GEMINI_NO_CONFIGURADO"));
    }

    @Test
    void laBusquedaPorSignificadoPideAlMenosTresPalabras() throws Exception {
        mvc.perform(como(agente, get("/api/articulos/busqueda-semantica").param("consulta", "datáfono caído")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        assertThat(gemini.consultas()).isEmpty();
    }

    // ---------- Tarea programada ----------

    private RegeneracionEmbeddings regeneracion() {
        return new RegeneracionEmbeddings(template, gemini, new PropiedadesGemini("", "http://localhost",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10));
    }

    @Test
    void laTareaProgramadaGeneraLosPendientesPorLotes() {
        articulo("Pendiente 1", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, null);
        articulo("Pendiente 2", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, null);
        articulo("Sintético", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, "sintetico/768");
        articulo("Vigente", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, GeminiEmbeddings.MODELO);

        assertThat(regeneracion().regenerar(2)).isEqualTo(2);
        assertThat(regeneracion().regenerar(10)).isEqualTo(1);
        assertThat(regeneracion().regenerar(10)).isZero();
        assertThat(gemini.documentos()).hasSize(3).noneMatch(t -> t.startsWith("Vigente"));
        assertThat(articulos.countDocuments(Filters.eq("embeddingModelo", GeminiEmbeddings.MODELO))).isEqualTo(4);
    }

    @Test
    void laTareaProgramadaNoPisaUnaEdicionHechaMientrasGeminiResponde() {
        Articulo a = articulo("Pendiente", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, null);
        gemini.antesDeResponder(() -> template.updateFirst(new Query(Criteria.where("_id").is(a.getId())),
                new Update().set("fechaActualizacion", ahora.plusSeconds(5)), Articulo.class));

        assertThat(regeneracion().regenerar(10)).isZero();
        assertThat(crudo(a.getId().toHexString())).containsEntry("embedding", null);
    }

    @Test
    void laTareaProgramadaSeDetieneSiSeAcabaLaCuota() {
        articulo("Pendiente 1", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, null);
        articulo("Pendiente 2", "C", ModuloErp.COMPRAS, Visibilidad.PUBLICO, ahora, null);
        gemini.fallarCon(Motivo.LIMITE_ALCANZADO);

        assertThat(regeneracion().regenerar(10)).isZero();
        assertThat(gemini.documentos()).hasSize(1);
    }

    /** El proveedor que reciben los servicios es el falso: ninguna prueba llama a Gemini. */
    @Test
    void lasPruebasNoUsanGemini(@Autowired ProveedorEmbeddings proveedor) {
        assertThat(proveedor).isSameAs(gemini).isNotInstanceOf(GeminiEmbeddings.class);
    }
}
