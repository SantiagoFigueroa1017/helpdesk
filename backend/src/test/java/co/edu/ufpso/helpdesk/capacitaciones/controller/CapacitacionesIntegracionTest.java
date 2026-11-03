package co.edu.ufpso.helpdesk.capacitaciones.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import com.mongodb.client.model.Filters;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoSolicitud;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.SolicitudCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
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
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Capacitaciones (HU33 a HU36) y solicitudes de capacitación personalizada de punta a punta, con token
 * real y los validadores reales del {@code rs0} local (tarea 2.8, parte 1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CapacitacionesIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

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
    private Membresia membresiaFerreteria;
    private Membresia membresiaPanaderia;
    private Usuario admin;
    private Usuario agente;
    private Usuario agenteSinAsistencia;
    private Usuario clienteAdminFerreteria;
    private Usuario clienteAdminPanaderia;
    private Usuario clienteFerreteria;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "planes", "membresias", "capacitaciones",
                "solicitudesCapacitacion", "inscripciones")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ValidadoresDePrueba.copiarIndices(mongoClient, template, "inscripciones",
                "ux_inscripciones_capacitacion_usuario");
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ferreteria = empresa("900123456-1", "Ferretería El Tornillo");
        panaderia = empresa("800765432-9", "Panadería La Espiga");
        plan = plan(1);
        membresiaFerreteria = membresia(ferreteria.getId(), EstadoMembresia.ACTIVA, ModuloErp.INVENTARIOS,
                ModuloErp.COMPRAS);
        membresiaPanaderia = membresia(panaderia.getId(), EstadoMembresia.ACTIVA, ModuloErp.VENTAS_EN_SITIO);

        Rol rolAdmin = rol("ADMIN", AlcanceRol.GLOBAL, List.of(Permiso.CAPACITACIONES_VER, Permiso.CAPACITACIONES_CREAR,
                Permiso.CAPACITACIONES_EDITAR, Permiso.CAPACITACIONES_ELIMINAR, Permiso.SOLICITUDES_GESTIONAR,
                Permiso.INSCRIPCIONES_GESTIONAR, Permiso.INSCRIPCIONES_VER, Permiso.ASISTENCIA_MARCAR));
        Rol rolAgente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.CAPACITACIONES_CREAR, Permiso.CAPACITACIONES_EDITAR, Permiso.SOLICITUDES_GESTIONAR,
                Permiso.INSCRIPCIONES_GESTIONAR, Permiso.INSCRIPCIONES_VER, Permiso.ASISTENCIA_MARCAR));
        Rol rolSinAsistencia = rol("SOLO_CAPACITACIONES", AlcanceRol.GLOBAL, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.CAPACITACIONES_CREAR));
        Rol rolClienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.CAPACITACIONES_SOLICITAR));
        Rol rolCliente = rol("CLIENTE", AlcanceRol.EMPRESA, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.INSCRIPCIONES_PROPIAS));

        admin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, rolAdmin);
        agente = usuario("Andrés Pabón", "andres@sistemasintegrados.com.co", null, rolAgente);
        agenteSinAsistencia = usuario("Paula Ardila", "paula@sistemasintegrados.com.co", null, rolSinAsistencia);
        clienteAdminFerreteria = usuario("Gloria Peña", "gloria@ferreteria.co", ferreteria.getId(), rolClienteAdmin);
        clienteAdminPanaderia = usuario("Rosa Jaimes", "rosa@panaderia.co", panaderia.getId(), rolClienteAdmin);
        clienteFerreteria = usuario("Camilo Quintero", "camilo@ferreteria.co", ferreteria.getId(), rolCliente);
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

    private Plan plan(int capacitacionesIncluidas) {
        Plan p = new Plan();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setNombre("Estándar");
        p.setDescripcion("Plan de prueba");
        p.setPrecio(new BigDecimal("900000"));
        p.setNivelAcceso(NivelAcceso.ESTANDAR);
        p.setLimites(new LimitesPlan(24, capacitacionesIncluidas, 10, 3));
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
        m.setFechaInicio(ahora.minus(90, ChronoUnit.DAYS));
        m.setFechaVencimiento(ahora.plus(20, ChronoUnit.DAYS));
        m.setEstado(estado);
        return template.insert(m);
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de CapacitacionesIntegracionTest");
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

    private static String cuerpoCapacitacion(String tipo, String modulo, ObjectId empresaId, Instant inicio, int cupo,
            Usuario instructor) {
        return "{\"titulo\":\"  Cierre de inventario  \",\"tipo\":\"" + tipo + "\",\"modulo\":\"" + modulo + "\","
                + (empresaId == null ? "" : "\"empresaId\":\"" + empresaId + "\",")
                + "\"fechaInicio\":\"" + inicio + "\",\"duracionMinutos\":90,\"cupo\":" + cupo + ","
                + "\"instructorId\":\"" + instructor.getId() + "\"}";
    }

    private static String cuerpoEdicion(Instant inicio, int cupo, Usuario instructor, String estado) {
        return "{\"titulo\":\"Cierre de inventario (reprogramada)\",\"fechaInicio\":\"" + inicio + "\","
                + "\"duracionMinutos\":120,\"cupo\":" + cupo + ",\"instructorId\":\"" + instructor.getId() + "\""
                + (estado == null ? "" : ",\"estado\":\"" + estado + "\"") + "}";
    }

    private static String cuerpoSolicitud(String modulo, LocalDate fechaPreferida) {
        return "{\"modulo\":\"" + modulo + "\",\"descripcion\":\"  Cierre mensual de bodegas  \","
                + "\"participantesEstimados\":8"
                + (fechaPreferida == null ? "" : ",\"fechaPreferida\":\"" + fechaPreferida + "\"") + "}";
    }

    private static String cuerpoProgramacion(Instant inicio, Usuario instructor) {
        return "{\"titulo\":\"Cierre de bodegas para la Ferretería\",\"fechaInicio\":\"" + inicio + "\","
                + "\"duracionMinutos\":60,\"cupo\":12,\"instructorId\":\"" + instructor.getId() + "\"}";
    }

    private static String id(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    private Document crudo(String coleccion, String id) {
        return template.getDb().getCollection(coleccion).find(Filters.eq("_id", new ObjectId(id))).first();
    }

    /** Capacitación insertada directo en la base, para controlar fechas y estado. */
    private Capacitacion capacitacion(String titulo, TipoCapacitacion tipo, ModuloErp modulo, ObjectId empresaId,
            Instant inicio, int cupo) {
        Capacitacion c = new Capacitacion();
        c.setFechaCreacion(ahora);
        c.setFechaActualizacion(ahora);
        c.setTitulo(titulo);
        c.setTipo(tipo);
        c.setModulo(modulo);
        c.setEmpresaId(empresaId);
        c.setFechaInicio(inicio);
        c.setDuracionMinutos(60);
        c.setCupo(cupo);
        c.setCuposOcupados(0);
        c.setInstructorId(agente.getId());
        c.setEstado(EstadoCapacitacion.PROGRAMADA);
        c.setSolicitudId(null);
        return template.insert(c);
    }

    /** Inscripción insertada directo en la base, con su cupo, como lo haría HU37. */
    private Inscripcion inscribir(Capacitacion c, Usuario u) {
        Inscripcion i = new Inscripcion();
        i.setFechaCreacion(ahora);
        i.setFechaActualizacion(ahora);
        i.setCapacitacionId(c.getId());
        i.setUsuarioId(u.getId());
        i.setEmpresaId(u.getEmpresaId());
        template.updateFirst(new Query(Criteria.where("_id").is(c.getId())), new Update().inc("cuposOcupados", 1),
                Capacitacion.class);
        return template.insert(i);
    }

    private SolicitudCapacitacion solicitud(Empresa empresa, Usuario solicitante, Instant creada) {
        SolicitudCapacitacion s = new SolicitudCapacitacion();
        s.setFechaCreacion(creada);
        s.setFechaActualizacion(creada);
        s.setEmpresaId(empresa.getId());
        s.setSolicitanteId(solicitante.getId());
        s.setModulo(ModuloErp.INVENTARIOS);
        s.setDescripcion("Necesitamos aprender los ajustes de existencias.");
        s.setParticipantesEstimados(6);
        return template.insert(s);
    }

    private Capacitacion leer(Capacitacion c) {
        return template.findById(c.getId(), Capacitacion.class);
    }

    private SolicitudCapacitacion leer(SolicitudCapacitacion s) {
        return template.findById(s.getId(), SolicitudCapacitacion.class);
    }

    private Instant enDias(int dias) {
        return ahora.plus(dias, ChronoUnit.DAYS);
    }

    // ---------- HU33 · crear ----------

    @Test
    void elAgenteProgramaUnaMasiva() throws Exception {
        Instant inicio = enDias(5);
        MvcResult resultado = mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "INVENTARIOS", null, inicio, 40, agente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Cierre de inventario"))
                .andExpect(jsonPath("$.tipo").value("MASIVA"))
                .andExpect(jsonPath("$.empresaId").doesNotExist())
                .andExpect(jsonPath("$.cuposDisponibles").value(40))
                .andExpect(jsonPath("$.instructorNombre").value("Andrés Pabón"))
                .andExpect(jsonPath("$.estadoVisible").value("PROGRAMADA"))
                .andExpect(jsonPath("$.inscritos").value(0))
                .andReturn();

        Document doc = crudo("capacitaciones", id(resultado));
        assertThat(doc.getDate("fechaFin").toInstant()).isEqualTo(inicio.plus(Duration.ofMinutes(90)));
        assertThat(doc).containsKeys("empresaId", "solicitudId");
        assertThat(doc.get("empresaId")).isNull();
        assertThat(doc.get("cuposOcupados")).isEqualTo(0);
        assertThat(doc).doesNotContainKey("_class");
    }

    @Test
    void crearRevisaFechaInstructorYEmpresa() throws Exception {
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "COMPRAS", null, ahora.minus(1, ChronoUnit.HOURS), 10, agente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("posteriores")));
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "COMPRAS", null, enDias(2), 10, agenteSinAsistencia)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("instructor")));
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "COMPRAS", null, enDias(2), 10, clienteFerreteria)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "COMPRAS", ferreteria.getId(), enDias(2), 10, agente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("masiva")));
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("PERSONALIZADA", "COMPRAS", null, enDias(2), 10, agente)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("empresaId"));
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "COMPRAS", null, enDias(2), 0, agente)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cupo"));
        assertThat(template.count(new Query(), Capacitacion.class)).isZero();
    }

    @Test
    void unaPersonalizadaExigeSoporteYModuloContratado() throws Exception {
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("PERSONALIZADA", "PRODUCCION", ferreteria.getId(), enDias(3), 10, agente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("Producción")));
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("PERSONALIZADA", "COMPRAS", new ObjectId(), enDias(3), 10, agente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("no existe")));

        template.updateFirst(new Query(Criteria.where("_id").is(membresiaFerreteria.getId())),
                new Update().set("estado", "SUSPENDIDA"), Membresia.class);
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("PERSONALIZADA", "COMPRAS", ferreteria.getId(), enDias(3), 10, agente)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOPORTE_BLOQUEADO"))
                .andExpect(jsonPath("$.motivo").value("SUSPENDIDA"));

        template.updateFirst(new Query(Criteria.where("_id").is(membresiaFerreteria.getId())),
                new Update().set("estado", "ACTIVA"), Membresia.class);
        mvc.perform(conCuerpo(agente, post("/api/capacitaciones"),
                        cuerpoCapacitacion("PERSONALIZADA", "COMPRAS", ferreteria.getId(), enDias(3), 10, agente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaNombre").value("Ferretería El Tornillo"))
                .andExpect(jsonPath("$.solicitudId").doesNotExist());
    }

    @Test
    void unClienteNoCreaCapacitaciones() throws Exception {
        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/capacitaciones"),
                        cuerpoCapacitacion("MASIVA", "COMPRAS", null, enDias(2), 10, agente)))
                .andExpect(status().isForbidden());
    }

    // ---------- HU34 · consultar ----------

    @Test
    void elClienteVeLasMasivasYLasPersonalizadasDeSuEmpresa() throws Exception {
        Capacitacion masiva = capacitacion("Masiva compras", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null,
                enDias(4), 30);
        capacitacion("Personalizada Ferretería", TipoCapacitacion.PERSONALIZADA, ModuloErp.INVENTARIOS,
                ferreteria.getId(), enDias(2), 10);
        Capacitacion ajena = capacitacion("Personalizada Panadería", TipoCapacitacion.PERSONALIZADA,
                ModuloErp.VENTAS_EN_SITIO, panaderia.getId(), enDias(3), 10);
        Inscripcion mia = inscribir(masiva, clienteFerreteria);

        mvc.perform(como(clienteFerreteria, get("/api/capacitaciones")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].titulo").value(contains("Personalizada Ferretería", "Masiva compras")))
                .andExpect(jsonPath("$.contenido[0].miInscripcionId").doesNotExist())
                .andExpect(jsonPath("$.contenido[1].miInscripcionId").value(mia.getId().toHexString()))
                .andExpect(jsonPath("$.contenido[1].cuposDisponibles").value(29))
                .andExpect(jsonPath("$.contenido[1].inscritos").doesNotExist());
        mvc.perform(como(clienteFerreteria, get("/api/capacitaciones/" + ajena.getId())))
                .andExpect(status().isNotFound());
        // El empresaId de la URL no aplica a un usuario de empresa.
        mvc.perform(como(clienteFerreteria, get("/api/capacitaciones").param("empresaId", panaderia.getId().toHexString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(como(agente, get("/api/capacitaciones")))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mvc.perform(como(agente, get("/api/capacitaciones").param("empresaId", panaderia.getId().toHexString())))
                .andExpect(jsonPath("$.contenido[*].titulo").value(contains("Personalizada Panadería")));
    }

    @Test
    void elCronogramaSeFiltraPorModuloTipoYFechas() throws Exception {
        capacitacion("Hoy en ocho días", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null, enDias(8), 30);
        capacitacion("Mañana", TipoCapacitacion.MASIVA, ModuloErp.INVENTARIOS, null, enDias(1), 30);
        capacitacion("En un mes", TipoCapacitacion.PERSONALIZADA, ModuloErp.COMPRAS, ferreteria.getId(), enDias(30), 5);
        LocalDate desde = LocalDate.ofInstant(enDias(7), BOGOTA);
        LocalDate hasta = LocalDate.ofInstant(enDias(9), BOGOTA);

        mvc.perform(como(agente, get("/api/capacitaciones").param("modulo", "COMPRAS")))
                .andExpect(jsonPath("$.contenido[*].titulo").value(contains("Hoy en ocho días", "En un mes")));
        mvc.perform(como(agente, get("/api/capacitaciones").param("tipo", "PERSONALIZADA")))
                .andExpect(jsonPath("$.contenido[*].titulo").value(contains("En un mes")));
        mvc.perform(como(agente, get("/api/capacitaciones").param("desde", desde.toString())
                        .param("hasta", hasta.toString())))
                .andExpect(jsonPath("$.contenido[*].titulo").value(contains("Hoy en ocho días")));
        mvc.perform(como(agente, get("/api/capacitaciones").param("orden", "fechaInicio,desc")))
                .andExpect(jsonPath("$.contenido[0].titulo").value("En un mes"));
        mvc.perform(como(agente, get("/api/capacitaciones").param("orden", "titulo,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void elEstadoVisibleSeDerivaDeLaHora() throws Exception {
        Capacitacion enCurso = capacitacion("En curso", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null,
                ahora.minus(10, ChronoUnit.MINUTES), 30);
        Capacitacion realizada = capacitacion("Realizada", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null,
                ahora.minus(3, ChronoUnit.DAYS), 30);

        mvc.perform(como(agente, get("/api/capacitaciones/" + enCurso.getId())))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$.estadoVisible").value("EN_CURSO"));
        mvc.perform(como(agente, get("/api/capacitaciones/" + realizada.getId())))
                .andExpect(jsonPath("$.estadoVisible").value("REALIZADA"));
    }

    // ---------- HU35 · actualizar ----------

    @Test
    void elAgenteReprogramaYCancela() throws Exception {
        Capacitacion c = capacitacion("Masiva", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null, enDias(4), 30);
        inscribir(c, clienteFerreteria);
        Instant nuevoInicio = enDias(6);

        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + c.getId()), cuerpoEdicion(nuevoInicio, 25, admin, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Cierre de inventario (reprogramada)"))
                .andExpect(jsonPath("$.cupo").value(25))
                .andExpect(jsonPath("$.cuposOcupados").value(1))
                .andExpect(jsonPath("$.instructorNombre").value("Liliana Contreras"))
                .andExpect(jsonPath("$.tipo").value("MASIVA"));
        Capacitacion editada = leer(c);
        assertThat(editada.getFechaFin()).isEqualTo(nuevoInicio.plus(Duration.ofMinutes(120)));
        assertThat(editada.getCuposOcupados()).isEqualTo(1);

        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + c.getId()),
                        cuerpoEdicion(nuevoInicio, 25, admin, "CANCELADA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoVisible").value("CANCELADA"));
        // La cancelación es definitiva.
        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + c.getId()),
                        cuerpoEdicion(nuevoInicio, 25, admin, "PROGRAMADA")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("cancelada")));
        assertThat(template.count(new Query(), Inscripcion.class)).isEqualTo(1);
    }

    @Test
    void elCupoNoQuedaPorDebajoDeLosInscritos() throws Exception {
        Capacitacion c = capacitacion("Masiva", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null, enDias(4), 30);
        inscribir(c, clienteFerreteria);
        inscribir(c, clienteAdminFerreteria);

        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + c.getId()), cuerpoEdicion(enDias(4), 1, agente, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("2 inscritos")));
        assertThat(leer(c).getCupo()).isEqualTo(30);
    }

    @Test
    void noSeModificaUnaRealizadaNiSeMueveAlPasado() throws Exception {
        Capacitacion realizada = capacitacion("Realizada", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null,
                ahora.minus(2, ChronoUnit.DAYS), 30);
        Capacitacion futura = capacitacion("Futura", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null, enDias(2), 30);

        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + realizada.getId()),
                        cuerpoEdicion(enDias(3), 30, agente, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("realizó")));
        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + futura.getId()),
                        cuerpoEdicion(ahora.minus(1, ChronoUnit.DAYS), 30, agente, null)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(conCuerpo(agente, put("/api/capacitaciones/" + futura.getId()),
                        cuerpoEdicion(enDias(2), 30, agenteSinAsistencia, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("instructor")));
    }

    // ---------- HU36 · eliminar ----------

    @Test
    void elAdminEliminaConSusInscripcionesYLaSolicitudVuelveAPendiente() throws Exception {
        SolicitudCapacitacion s = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));
        String capacitacionId = id(mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId()
                        + "/programar"), cuerpoProgramacion(enDias(5), agente)))
                .andExpect(status().isOk()).andReturn());
        Capacitacion c = template.findById(new ObjectId(capacitacionId), Capacitacion.class);
        inscribir(c, clienteFerreteria);

        mvc.perform(como(admin, get("/api/capacitaciones/" + capacitacionId)))
                .andExpect(jsonPath("$.inscritos").value(1));
        mvc.perform(como(agente, delete("/api/capacitaciones/" + capacitacionId)))
                .andExpect(status().isForbidden());
        mvc.perform(como(admin, delete("/api/capacitaciones/" + capacitacionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscripcionesEliminadas").value(1))
                .andExpect(jsonPath("$.solicitudDevuelta").value(true));

        assertThat(leer(c)).isNull();
        assertThat(template.count(new Query(), Inscripcion.class)).isZero();
        SolicitudCapacitacion devuelta = leer(s);
        assertThat(devuelta.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(devuelta.getCapacitacionId()).isNull();
        assertThat(devuelta.getAtendidaPorId()).isEqualTo(agente.getId());
        mvc.perform(como(admin, delete("/api/capacitaciones/" + capacitacionId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void siUnPasoDelBorradoFallaNoSeBorraNada() throws Exception {
        SolicitudCapacitacion s = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));
        String capacitacionId = id(mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId()
                        + "/programar"), cuerpoProgramacion(enDias(5), agente)))
                .andExpect(status().isOk()).andReturn());
        Capacitacion c = template.findById(new ObjectId(capacitacionId), Capacitacion.class);
        inscribir(c, clienteFerreteria);
        // Las solicitudes de la base de pruebas ya no aceptan volver a PENDIENTE: el segundo paso falla
        // después de borrar las inscripciones, dentro de la transacción.
        template.getDb().runCommand(new Document("collMod", "solicitudesCapacitacion")
                .append("validator", new Document("estado", new Document("$in", List.of("PROGRAMADA", "RECHAZADA")))));

        mvc.perform(como(admin, delete("/api/capacitaciones/" + capacitacionId)))
                .andExpect(status().isInternalServerError());

        assertThat(leer(c)).isNotNull();
        assertThat(template.count(new Query(), Inscripcion.class)).isEqualTo(1);
        assertThat(leer(s).getEstado()).isEqualTo(EstadoSolicitud.PROGRAMADA);
    }

    // ---------- solicitudes · crear ----------

    @Test
    void elClienteAdminRegistraUnaSolicitud() throws Exception {
        LocalDate preferida = LocalDate.ofInstant(enDias(10), BOGOTA);
        MvcResult resultado = mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("INVENTARIOS", preferida)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.descripcion").value("Cierre mensual de bodegas"))
                .andExpect(jsonPath("$.empresaNombre").value("Ferretería El Tornillo"))
                .andExpect(jsonPath("$.solicitanteNombre").value("Gloria Peña"))
                .andExpect(jsonPath("$.fechaPreferida").value(preferida.toString()))
                .andReturn();

        Document doc = crudo("solicitudesCapacitacion", id(resultado));
        assertThat(doc).containsKeys("capacitacionId", "motivoRechazo", "atendidaPorId");
        assertThat(doc.get("capacitacionId")).isNull();
        assertThat(doc).doesNotContainKey("_class");
    }

    @Test
    void unaSolicitudExigeSoporteModuloYFechaValida() throws Exception {
        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("PRODUCCION", null)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("COMPRAS", LocalDate.ofInstant(ahora, BOGOTA).minusDays(1))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("anterior a hoy")));

        template.updateFirst(new Query(Criteria.where("_id").is(membresiaFerreteria.getId())),
                new Update().set("fechaVencimiento", ahora.minus(1, ChronoUnit.DAYS)), Membresia.class);
        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("COMPRAS", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("VENCIDA"));
        mvc.perform(conCuerpo(agente, post("/api/solicitudes-capacitacion"), cuerpoSolicitud("COMPRAS", null)))
                .andExpect(status().isForbidden());
        assertThat(template.count(new Query(), SolicitudCapacitacion.class)).isZero();
    }

    @Test
    void elLimiteDelPlanCuentaElMesPagado() throws Exception {
        // Plan con 1 capacitación incluida. Una solicitud de hace dos meses no cuenta; una rechazada tampoco.
        solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(60, ChronoUnit.DAYS));
        SolicitudCapacitacion rechazada = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(2, ChronoUnit.DAYS));
        rechazada.rechazar("Sin instructor disponible", agente.getId());
        template.save(rechazada);

        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("COMPRAS", null)))
                .andExpect(status().isCreated());
        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("COMPRAS", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("incluye 1")));

        // Otra empresa con el mismo plan tiene su propio cupo.
        mvc.perform(conCuerpo(clienteAdminPanaderia, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("VENTAS_EN_SITIO", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void unPlanSinCapacitacionesIncluidasNoAdmiteSolicitudes() throws Exception {
        template.updateFirst(new Query(Criteria.where("_id").is(plan.getId())),
                new Update().set("limites.capacitacionesIncluidas", 0), Plan.class);

        mvc.perform(conCuerpo(clienteAdminFerreteria, post("/api/solicitudes-capacitacion"),
                        cuerpoSolicitud("COMPRAS", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("no incluye")));
    }

    // ---------- solicitudes · consultar ----------

    @Test
    void cadaUnoVeLasSolicitudesQueLeCorresponden() throws Exception {
        SolicitudCapacitacion vieja = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(3, ChronoUnit.DAYS));
        SolicitudCapacitacion nueva = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));
        SolicitudCapacitacion ajena = solicitud(panaderia, clienteAdminPanaderia, ahora.minus(2, ChronoUnit.DAYS));

        mvc.perform(como(clienteAdminFerreteria, get("/api/solicitudes-capacitacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].id").value(contains(nueva.getId().toHexString(),
                        vieja.getId().toHexString())));
        mvc.perform(como(clienteAdminFerreteria, get("/api/solicitudes-capacitacion/" + ajena.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(como(agente, get("/api/solicitudes-capacitacion").param("estado", "PENDIENTE")))
                .andExpect(jsonPath("$.contenido[*].id").value(contains(vieja.getId().toHexString(),
                        ajena.getId().toHexString(), nueva.getId().toHexString())));
        mvc.perform(como(clienteFerreteria, get("/api/solicitudes-capacitacion")))
                .andExpect(status().isForbidden());
    }

    // ---------- solicitudes · programar y rechazar ----------

    @Test
    void programarCreaLaPersonalizadaYMarcaLaSolicitud() throws Exception {
        SolicitudCapacitacion s = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));

        MvcResult resultado = mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId()
                        + "/programar"), cuerpoProgramacion(enDias(5), agente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PERSONALIZADA"))
                .andExpect(jsonPath("$.modulo").value("INVENTARIOS"))
                .andExpect(jsonPath("$.empresaId").value(ferreteria.getId().toHexString()))
                .andExpect(jsonPath("$.solicitudId").value(s.getId().toHexString()))
                .andReturn();

        SolicitudCapacitacion programada = leer(s);
        assertThat(programada.getEstado()).isEqualTo(EstadoSolicitud.PROGRAMADA);
        assertThat(programada.getCapacitacionId()).isEqualTo(new ObjectId(id(resultado)));
        assertThat(programada.getAtendidaPorId()).isEqualTo(agente.getId());

        mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId() + "/programar"),
                        cuerpoProgramacion(enDias(6), agente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("pendiente")));
        mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId() + "/rechazar"),
                        "{\"motivo\":\"Tarde\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(como(clienteAdminFerreteria, delete("/api/solicitudes-capacitacion/" + s.getId())))
                .andExpect(status().isUnprocessableContent());
        assertThat(template.count(new Query(), Capacitacion.class)).isEqualTo(1);
    }

    @Test
    void programarParaUnaEmpresaSinSoporteNoCreaNada() throws Exception {
        SolicitudCapacitacion s = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));
        template.updateFirst(new Query(Criteria.where("_id").is(membresiaFerreteria.getId())),
                new Update().set("estado", "SUSPENDIDA"), Membresia.class);

        mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId() + "/programar"),
                        cuerpoProgramacion(enDias(5), agente)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOPORTE_BLOQUEADO"));
        assertThat(template.count(new Query(), Capacitacion.class)).isZero();
        assertThat(leer(s).getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void siMarcarLaSolicitudFallaNoQuedaLaCapacitacion() throws Exception {
        SolicitudCapacitacion s = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));
        // Las solicitudes de la base de pruebas ya no aceptan PROGRAMADA: el reemplazo falla después de
        // insertar la capacitación, dentro de la transacción.
        template.getDb().runCommand(new Document("collMod", "solicitudesCapacitacion")
                .append("validator", new Document("estado", new Document("$in", List.of("PENDIENTE", "RECHAZADA")))));

        mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId() + "/programar"),
                        cuerpoProgramacion(enDias(5), agente)))
                .andExpect(status().isInternalServerError());

        assertThat(template.count(new Query(), Capacitacion.class)).isZero();
        assertThat(leer(s).getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void rechazarGuardaElMotivoYLuegoSePuedeEliminar() throws Exception {
        SolicitudCapacitacion s = solicitud(ferreteria, clienteAdminFerreteria, ahora.minus(1, ChronoUnit.DAYS));

        mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId() + "/rechazar"), "{\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(conCuerpo(clienteAdminFerreteria, put("/api/solicitudes-capacitacion/" + s.getId() + "/rechazar"),
                        "{\"motivo\":\"Prefiero otra fecha\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(conCuerpo(agente, put("/api/solicitudes-capacitacion/" + s.getId() + "/rechazar"),
                        "{\"motivo\":\"  No hay instructor para Inventarios ese mes  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("No hay instructor para Inventarios ese mes"))
                .andExpect(jsonPath("$.atendidaPorNombre").value("Andrés Pabón"));

        mvc.perform(como(clienteAdminPanaderia, delete("/api/solicitudes-capacitacion/" + s.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(como(clienteAdminFerreteria, delete("/api/solicitudes-capacitacion/" + s.getId())))
                .andExpect(status().isNoContent());
        assertThat(leer(s)).isNull();
    }

    @Test
    void lasRespuestasNoExponenCamposInternos() throws Exception {
        Capacitacion c = capacitacion("Masiva", TipoCapacitacion.MASIVA, ModuloErp.COMPRAS, null, enDias(4), 30);
        mvc.perform(como(clienteFerreteria, get("/api/capacitaciones/" + c.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").doesNotExist())
                .andExpect(jsonPath("$._class").doesNotExist());
    }
}
