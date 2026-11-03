package co.edu.ufpso.helpdesk.capacitaciones.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
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
 * Inscripciones y asistencias (HU37 a HU40) de punta a punta, con token real y los validadores e
 * índices reales del {@code rs0} local (tarea 2.8, parte 2). Incluye la carrera por el último cupo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InscripcionesIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

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
    private Rol rolCliente;
    private Usuario admin;
    private Usuario agente;
    private Usuario otroAgente;
    private Usuario clienteAdminFerreteria;
    private Usuario clienteFerreteria;
    private Usuario otroClienteFerreteria;
    private Usuario clientePanaderia;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "planes", "membresias", "capacitaciones",
                "solicitudesCapacitacion", "inscripciones")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ValidadoresDePrueba.copiarIndices(mongoClient, template, "inscripciones",
                "ux_inscripciones_capacitacion_usuario", "ix_inscripciones_usuario_fecha");
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ferreteria = empresa("900123456-1", "Ferretería El Tornillo");
        panaderia = empresa("800765432-9", "Panadería La Espiga");
        plan = plan();
        membresiaFerreteria = membresia(ferreteria.getId(), ModuloErp.INVENTARIOS, ModuloErp.COMPRAS);
        membresia(panaderia.getId(), ModuloErp.VENTAS_EN_SITIO);

        Rol rolAdmin = rol("ADMIN", AlcanceRol.GLOBAL, List.of(Permiso.CAPACITACIONES_VER, Permiso.CAPACITACIONES_CREAR,
                Permiso.CAPACITACIONES_EDITAR, Permiso.CAPACITACIONES_ELIMINAR, Permiso.INSCRIPCIONES_GESTIONAR,
                Permiso.INSCRIPCIONES_VER, Permiso.ASISTENCIA_MARCAR));
        Rol rolAgente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.CAPACITACIONES_CREAR, Permiso.CAPACITACIONES_EDITAR, Permiso.INSCRIPCIONES_GESTIONAR,
                Permiso.INSCRIPCIONES_VER, Permiso.ASISTENCIA_MARCAR));
        Rol rolClienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.CAPACITACIONES_SOLICITAR));
        rolCliente = rol("CLIENTE", AlcanceRol.EMPRESA, List.of(Permiso.CAPACITACIONES_VER,
                Permiso.INSCRIPCIONES_PROPIAS));

        admin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, rolAdmin);
        agente = usuario("Andrés Pabón", "andres@sistemasintegrados.com.co", null, rolAgente);
        otroAgente = usuario("Paula Ardila", "paula@sistemasintegrados.com.co", null, rolAgente);
        clienteAdminFerreteria = usuario("Gloria Peña", "gloria@ferreteria.co", ferreteria.getId(), rolClienteAdmin);
        clienteFerreteria = usuario("Camilo Quintero", "camilo@ferreteria.co", ferreteria.getId(), rolCliente);
        otroClienteFerreteria = usuario("Diana Rincón", "diana@ferreteria.co", ferreteria.getId(), rolCliente);
        clientePanaderia = usuario("Rosa Jaimes", "rosa@panaderia.co", panaderia.getId(), rolCliente);
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
        p.setNombre("Premium");
        p.setDescripcion("Plan de prueba");
        p.setPrecio(new BigDecimal("1800000"));
        p.setNivelAcceso(NivelAcceso.PREMIUM);
        p.setLimites(new LimitesPlan(8, 6, 30, 5));
        p.setActivo(true);
        return template.insert(p);
    }

    private Membresia membresia(ObjectId empresaId, ModuloErp... modulos) {
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(empresaId);
        m.setPlanId(plan.getId());
        m.setModulosContratados(List.of(modulos));
        m.setFechaInicio(ahora.minus(60, ChronoUnit.DAYS));
        m.setFechaVencimiento(ahora.plus(20, ChronoUnit.DAYS));
        m.setEstado(EstadoMembresia.ACTIVA);
        return template.insert(m);
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de InscripcionesIntegracionTest");
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

    /** POST de inscripción; {@code usuario} null = se inscribe el propio actor, sin cuerpo. */
    private MockHttpServletRequestBuilder inscribirComo(Usuario actor, Capacitacion c, Usuario usuario) {
        MockHttpServletRequestBuilder peticion = como(actor, post("/api/capacitaciones/" + c.getId() + "/inscripciones"));
        return usuario == null ? peticion
                : peticion.contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":\"" + usuario.getId() + "\"}");
    }

    private MockHttpServletRequestBuilder marcarComo(Usuario actor, String inscripcionId, boolean asistio) {
        return como(actor, put("/api/inscripciones/" + inscripcionId + "/asistencia"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"asistio\":" + asistio + "}");
    }

    private static String id(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    private Capacitacion capacitacion(TipoCapacitacion tipo, ObjectId empresaId, Instant inicio, int cupo) {
        Capacitacion c = new Capacitacion();
        c.setFechaCreacion(ahora);
        c.setFechaActualizacion(ahora);
        c.setTitulo(tipo == TipoCapacitacion.MASIVA ? "Inventarios para todos" : "Inventarios a la medida");
        c.setTipo(tipo);
        c.setModulo(ModuloErp.INVENTARIOS);
        c.setEmpresaId(empresaId);
        c.setFechaInicio(inicio);
        c.setDuracionMinutos(120);
        c.setCupo(cupo);
        c.setCuposOcupados(0);
        c.setInstructorId(agente.getId());
        c.setEstado(EstadoCapacitacion.PROGRAMADA);
        c.setSolicitudId(null);
        return template.insert(c);
    }

    private Capacitacion masiva(int cupo) {
        return capacitacion(TipoCapacitacion.MASIVA, null, ahora.plus(3, ChronoUnit.DAYS), cupo);
    }

    /** Mueve la sesión al pasado reciente, como si ya hubiera empezado. */
    private void empezar(Capacitacion c) {
        Instant inicio = ahora.minus(10, ChronoUnit.MINUTES);
        template.updateFirst(new Query(Criteria.where("_id").is(c.getId())),
                new Update().set("fechaInicio", inicio).set("fechaFin", inicio.plus(120, ChronoUnit.MINUTES)),
                Capacitacion.class);
    }

    private int cupos(Capacitacion c) {
        return template.findById(c.getId(), Capacitacion.class).getCuposOcupados();
    }

    private long inscripciones() {
        return template.count(new Query(), Inscripcion.class);
    }

    private Inscripcion inscripcion(String id) {
        return template.findById(new ObjectId(id), Inscripcion.class);
    }

    // ---------- HU37 · inscribir ----------

    @Test
    void elClienteSeInscribeAUnaMasivaYOcupaUnCupo() throws Exception {
        Capacitacion c = masiva(30);

        MvcResult resultado = mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacitacionTitulo").value("Inventarios para todos"))
                .andExpect(jsonPath("$.usuarioNombre").value("Camilo Quintero"))
                .andExpect(jsonPath("$.empresaNombre").value("Ferretería El Tornillo"))
                .andExpect(jsonPath("$.asistio").doesNotExist())
                .andReturn();

        assertThat(cupos(c)).isEqualTo(1);
        Document doc = template.getDb().getCollection("inscripciones")
                .find(Filters.eq("_id", new ObjectId(id(resultado)))).first();
        assertThat(doc).containsKeys("asistio", "marcadoPorId", "fechaMarcado");
        assertThat(doc.get("empresaId")).isEqualTo(ferreteria.getId());
        assertThat(doc).doesNotContainKey("_class");

        mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        assertThat(cupos(c)).isEqualTo(1);
        mvc.perform(como(clienteFerreteria, get("/api/capacitaciones/" + c.getId())))
                .andExpect(jsonPath("$.miInscripcionId").value(id(resultado)))
                .andExpect(jsonPath("$.cuposDisponibles").value(29));
    }

    @Test
    void noSeInscribeSinCupoNiEnUnaCanceladaNiEnUnaQueYaEmpezo() throws Exception {
        Capacitacion llena = masiva(1);
        mvc.perform(inscribirComo(clienteFerreteria, llena, null)).andExpect(status().isCreated());
        mvc.perform(inscribirComo(otroClienteFerreteria, llena, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("cupos")));

        Capacitacion cancelada = masiva(10);
        template.updateFirst(new Query(Criteria.where("_id").is(cancelada.getId())),
                new Update().set("estado", "CANCELADA"), Capacitacion.class);
        mvc.perform(inscribirComo(clienteFerreteria, cancelada, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("cancelada")));

        Capacitacion empezada = masiva(10);
        empezar(empezada);
        mvc.perform(inscribirComo(clienteFerreteria, empezada, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("empezó")));
        assertThat(inscripciones()).isEqualTo(1);
    }

    @Test
    void enLasPersonalizadasInscribeElAgenteAUsuariosDeEsaEmpresa() throws Exception {
        Capacitacion c = capacitacion(TipoCapacitacion.PERSONALIZADA, ferreteria.getId(),
                ahora.plus(2, ChronoUnit.DAYS), 10);

        mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("agente")));
        mvc.perform(inscribirComo(clientePanaderia, c, null))
                .andExpect(status().isNotFound());
        mvc.perform(inscribirComo(agente, c, clientePanaderia))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("empresa")));
        // Un CLIENTE_ADMIN sin el rol CLIENTE no se inscribe.
        mvc.perform(inscribirComo(agente, c, clienteAdminFerreteria))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(inscribirComo(agente, c, agente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("usuarioId"));
        mvc.perform(inscribirComo(agente, c, clienteFerreteria))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(clienteFerreteria.getId().toHexString()));
        assertThat(cupos(c)).isEqualTo(1);
    }

    @Test
    void unClienteNoInscribeAOtro() throws Exception {
        mvc.perform(inscribirComo(clienteFerreteria, masiva(10), otroClienteFerreteria))
                .andExpect(status().isForbidden());
        assertThat(inscripciones()).isZero();
    }

    @Test
    void sinSoporteNadieInscribeAUsuariosDeEsaEmpresa() throws Exception {
        Capacitacion c = masiva(10);
        template.updateFirst(new Query(Criteria.where("_id").is(membresiaFerreteria.getId())),
                new Update().set("estado", "PENDIENTE"), Membresia.class);

        mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOPORTE_BLOQUEADO"))
                .andExpect(jsonPath("$.motivo").value("PENDIENTE"));
        mvc.perform(inscribirComo(agente, c, clienteFerreteria))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOPORTE_BLOQUEADO"));
        mvc.perform(inscribirComo(clientePanaderia, c, null))
                .andExpect(status().isCreated());
        assertThat(cupos(c)).isEqualTo(1);
    }

    @Test
    void variasPersonasPorElUltimoCupoSoloUnaLoObtiene() throws Exception {
        Capacitacion c = masiva(1);
        List<Usuario> clientes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            clientes.add(usuario("Cliente " + i, "cliente" + i + "@ferreteria.co", ferreteria.getId(), rolCliente));
        }
        List<String> tokens = clientes.stream().map(this::token).toList();

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(clientes.size());
        List<Future<Integer>> respuestas = new ArrayList<>();
        try {
            for (String t : tokens) {
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/capacitaciones/" + c.getId() + "/inscripciones")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + t)).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> f : respuestas) {
                estados.add(f.get());
            }
            assertThat(estados).filteredOn(e -> e == 201).hasSize(1);
            // Las demás ven el cupo lleno (422) o, si chocaron tres veces seguidas, 409.
            assertThat(estados).filteredOn(e -> e != 201).allMatch(e -> e == 422 || e == 409);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(cupos(c)).isEqualTo(1);
        assertThat(inscripciones()).isEqualTo(1);
    }

    @Test
    void siLaInscripcionNoSeGuardaElCupoNoSeOcupa() throws Exception {
        Capacitacion c = masiva(10);
        // Las inscripciones de la base de pruebas ya no aceptan documentos: el insert falla después de
        // tomar el cupo, dentro de la transacción.
        template.getDb().runCommand(new Document("collMod", "inscripciones")
                .append("validator", new Document("capacitacionId", new Document("$exists", false))));

        mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isInternalServerError());
        assertThat(cupos(c)).isZero();
        assertThat(inscripciones()).isZero();
    }

    // ---------- HU38 · consultar ----------

    @Test
    void elAgenteVeLosInscritosYElClienteSoloLasSuyas() throws Exception {
        Capacitacion c = masiva(10);
        Capacitacion otra = capacitacion(TipoCapacitacion.MASIVA, null, ahora.plus(5, ChronoUnit.DAYS), 10);
        mvc.perform(inscribirComo(clienteFerreteria, c, null)).andExpect(status().isCreated());
        mvc.perform(inscribirComo(clientePanaderia, c, null)).andExpect(status().isCreated());
        mvc.perform(inscribirComo(clienteFerreteria, otra, null)).andExpect(status().isCreated());

        mvc.perform(como(agente, get("/api/capacitaciones/" + c.getId() + "/inscripciones")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].usuarioNombre").value(contains("Camilo Quintero", "Rosa Jaimes")));
        mvc.perform(como(agente, get("/api/capacitaciones/" + c.getId() + "/inscripciones").param("orden", "usuarioId,asc")))
                .andExpect(status().isBadRequest());
        mvc.perform(como(clienteFerreteria, get("/api/capacitaciones/" + c.getId() + "/inscripciones")))
                .andExpect(status().isForbidden());

        mvc.perform(como(clienteFerreteria, get("/api/inscripciones/mias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].capacitacionEstadoVisible").value("PROGRAMADA"))
                .andExpect(jsonPath("$.contenido[0].capacitacionFechaInicio").exists());
        mvc.perform(como(clientePanaderia, get("/api/inscripciones/mias")))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    // ---------- HU39 · asistencia ----------

    @Test
    void elInstructorMarcaDesdeLaHoraDeInicio() throws Exception {
        Capacitacion c = masiva(10);
        String inscripcionId = id(mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(marcarComo(agente, inscripcionId, true))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("hora de inicio")));

        empezar(c);
        mvc.perform(marcarComo(otroAgente, inscripcionId, true))
                .andExpect(status().isForbidden());
        mvc.perform(marcarComo(clienteFerreteria, inscripcionId, true))
                .andExpect(status().isForbidden());
        mvc.perform(marcarComo(agente, inscripcionId, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistio").value(true))
                .andExpect(jsonPath("$.marcadoPorNombre").value("Andrés Pabón"))
                .andExpect(jsonPath("$.capacitacionEstadoVisible").value("EN_CURSO"));
        // El administrador marca en cualquier capacitación, y se puede corregir.
        mvc.perform(marcarComo(admin, inscripcionId, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistio").value(false))
                .andExpect(jsonPath("$.marcadoPorNombre").value("Liliana Contreras"));

        Inscripcion marcada = inscripcion(inscripcionId);
        assertThat(marcada.getAsistio()).isFalse();
        assertThat(marcada.getMarcadoPorId()).isEqualTo(admin.getId());
        assertThat(marcada.getFechaMarcado()).isNotNull();
    }

    @Test
    void enUnaCanceladaNoSeMarca() throws Exception {
        Capacitacion c = masiva(10);
        String inscripcionId = id(mvc.perform(inscribirComo(clienteFerreteria, c, null))
                .andExpect(status().isCreated()).andReturn());
        empezar(c);
        template.updateFirst(new Query(Criteria.where("_id").is(c.getId())), new Update().set("estado", "CANCELADA"),
                Capacitacion.class);

        mvc.perform(marcarComo(agente, inscripcionId, true))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("cancelada")));
    }

    @Test
    void laListaSeMarcaEnUnaPeticion() throws Exception {
        Capacitacion c = masiva(10);
        Capacitacion otra = masiva(10);
        String camilo = id(mvc.perform(inscribirComo(clienteFerreteria, c, null)).andReturn());
        String diana = id(mvc.perform(inscribirComo(otroClienteFerreteria, c, null)).andReturn());
        String ajena = id(mvc.perform(inscribirComo(clientePanaderia, otra, null)).andReturn());
        empezar(c);

        String conAjena = "{\"marcas\":[{\"inscripcionId\":\"" + camilo + "\",\"asistio\":true},"
                + "{\"inscripcionId\":\"" + ajena + "\",\"asistio\":true}]}";
        mvc.perform(como(agente, put("/api/capacitaciones/" + c.getId() + "/asistencia"))
                        .contentType(MediaType.APPLICATION_JSON).content(conAjena))
                .andExpect(status().isUnprocessableContent());
        assertThat(inscripcion(camilo).getAsistio()).isNull();

        String lista = "{\"marcas\":[{\"inscripcionId\":\"" + camilo + "\",\"asistio\":true},"
                + "{\"inscripcionId\":\"" + diana + "\",\"asistio\":false}]}";
        mvc.perform(como(agente, put("/api/capacitaciones/" + c.getId() + "/asistencia"))
                        .contentType(MediaType.APPLICATION_JSON).content(lista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(2));
        assertThat(inscripcion(camilo).getAsistio()).isTrue();
        assertThat(inscripcion(diana).getAsistio()).isFalse();
        assertThat(inscripcion(diana).getMarcadoPorId()).isEqualTo(agente.getId());

        mvc.perform(como(agente, put("/api/capacitaciones/" + c.getId() + "/asistencia"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marcas\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- HU40 · cancelar ----------

    @Test
    void elClienteCancelaLaSuyaAntesDeLaFechaYLiberaElCupo() throws Exception {
        Capacitacion c = masiva(10);
        String mia = id(mvc.perform(inscribirComo(clienteFerreteria, c, null)).andReturn());
        String deDiana = id(mvc.perform(inscribirComo(otroClienteFerreteria, c, null)).andReturn());
        assertThat(cupos(c)).isEqualTo(2);

        mvc.perform(como(clienteFerreteria, delete("/api/inscripciones/" + deDiana)))
                .andExpect(status().isNotFound());
        mvc.perform(como(clienteFerreteria, delete("/api/inscripciones/" + mia)))
                .andExpect(status().isNoContent());
        assertThat(inscripcion(mia)).isNull();
        assertThat(cupos(c)).isEqualTo(1);

        empezar(c);
        mvc.perform(como(otroClienteFerreteria, delete("/api/inscripciones/" + deDiana)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("antes de la fecha")));
    }

    @Test
    void elPersonalDeSoporteCancelaCualquieraSinAsistenciaMarcada() throws Exception {
        Capacitacion c = masiva(10);
        String camilo = id(mvc.perform(inscribirComo(clienteFerreteria, c, null)).andReturn());
        String diana = id(mvc.perform(inscribirComo(otroClienteFerreteria, c, null)).andReturn());
        empezar(c);
        mvc.perform(marcarComo(agente, diana, true)).andExpect(status().isOk());

        mvc.perform(como(admin, delete("/api/inscripciones/" + diana)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("asistencia")));
        mvc.perform(como(admin, delete("/api/inscripciones/" + camilo)))
                .andExpect(status().isNoContent());
        assertThat(cupos(c)).isEqualTo(1);
        assertThat(inscripciones()).isEqualTo(1);
    }

    @Test
    void siLiberarElCupoFallaLaInscripcionNoSeBorra() throws Exception {
        Capacitacion c = masiva(10);
        String mia = id(mvc.perform(inscribirComo(clienteFerreteria, c, null)).andReturn());
        // Las capacitaciones de la base de pruebas ya no aceptan quedar con 0 cupos ocupados: el $inc
        // falla después de borrar la inscripción, dentro de la transacción.
        template.getDb().runCommand(new Document("collMod", "capacitaciones")
                .append("validator", new Document("cuposOcupados", new Document("$gte", 1))));

        mvc.perform(como(clienteFerreteria, delete("/api/inscripciones/" + mia)))
                .andExpect(status().isInternalServerError());
        assertThat(inscripcion(mia)).isNotNull();
        assertThat(cupos(c)).isEqualTo(1);
    }
}
