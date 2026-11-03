package co.edu.ufpso.helpdesk.planes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mongodb.client.MongoClient;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.planes.domain.LimitesPlan;
import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import co.edu.ufpso.helpdesk.planes.domain.Plan;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * CRUD de planes (HU09 a HU12) de punta a punta: token real, permisos leídos de MongoDB local y
 * validadores reales de {@code roles}, {@code usuarios}, {@code planes} y {@code membresias} en
 * {@code helpdesk_test} (tarea 2.5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanesIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private JwtEncoder encoder;

    private Usuario usuarioAdmin;
    private Usuario usuarioAgente;
    private Usuario usuarioClienteAdmin;
    private Plan basico;
    private Plan antiguo;

    @BeforeEach
    void prepararDatos() {
        ValidadoresDePrueba.copiar(mongoClient, template, "roles");
        ValidadoresDePrueba.copiar(mongoClient, template, "usuarios");
        ValidadoresDePrueba.copiar(mongoClient, template, "planes");
        ValidadoresDePrueba.copiar(mongoClient, template, "membresias");

        Rol admin = rol("ADMIN", AlcanceRol.GLOBAL, Arrays.asList(Permiso.values()));
        Rol agente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.PLANES_VER));
        Rol clienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA, List.of(Permiso.PLANES_VER));
        usuarioAdmin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, admin);
        usuarioAgente = usuario("Jhon Pacheco", "jhon@sistemasintegrados.com.co", null, agente);
        usuarioClienteAdmin = usuario("Camilo Quintero", "camilo@ferreteria.co", new ObjectId(), clienteAdmin);

        basico = plan("Básico", "150000", true);
        antiguo = plan("Antiguo", "90000", false);
    }

    // ---------- utilidades ----------

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Instant ahora = Instant.now();
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de PlanesIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(true);
        r.setEsSistema(true);
        return template.insert(r);
    }

    private Usuario usuario(String nombre, String email, ObjectId empresaId, Rol rol) {
        Instant ahora = Instant.now();
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

    private Plan plan(String nombre, String precio, boolean activo) {
        Instant ahora = Instant.now();
        Plan p = new Plan();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setNombre(nombre);
        p.setDescripcion("Plan de PlanesIntegracionTest");
        p.setPrecio(new BigDecimal(precio));
        p.setNivelAcceso(NivelAcceso.BASICO);
        p.setLimites(new LimitesPlan(48, 0, 5, 2));
        p.setActivo(activo);
        return template.insert(p);
    }

    private Membresia membresia(Plan plan) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(new ObjectId());
        m.setPlanId(plan.getId());
        m.setModulosContratados(List.of(ModuloErp.INVENTARIOS));
        m.setFechaInicio(ahora);
        m.setFechaVencimiento(ahora.plus(30, ChronoUnit.DAYS));
        m.setEstado(EstadoMembresia.ACTIVA);
        return template.insert(m);
    }

    private String token(Usuario u) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(u.getId().toHexString())
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(900)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private MockHttpServletRequestBuilder como(Usuario u, MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(u));
    }

    private static String cuerpo(String nombre, String precio, int maxModulos, Boolean activo) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Soporte en horario laboral\",\"precio\":" + precio
                + ",\"nivelAcceso\":\"ESTANDAR\",\"limites\":{\"horasRespuesta\":24,\"capacitacionesIncluidas\":2,"
                + "\"maxUsuarios\":15,\"maxModulos\":" + maxModulos + "}"
                + (activo == null ? "" : ",\"activo\":" + activo) + "}";
    }

    // ---------- HU09 · crear ----------

    @Test
    void creaUnPlanActivoConElPrecioComoDecimal128() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/planes")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("  Estándar ", "320000.50", 3, null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/planes/")))
                .andExpect(jsonPath("$.nombre").value("Estándar"))
                .andExpect(jsonPath("$.precio").value(320000.50))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.limites.maxModulos").value(3));

        Document crudo = template.getCollection("planes").find(new Document("nombre", "Estándar")).first();
        assertThat(crudo).isNotNull();
        assertThat(crudo.get("precio")).isInstanceOf(Decimal128.class);
        assertThat(crudo.get("precio", Decimal128.class).bigDecimalValue()).isEqualByComparingTo("320000.50");
    }

    @Test
    void unNombreRepetidoResponde409() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/planes")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Básico", "100000", 2, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void precioCeroOMasDeCincoModulosResponde400() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/planes")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Gratis", "0", 2, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("precio"));
        mvc.perform(como(usuarioAdmin, post("/api/planes")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Todo", "500000", 6, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("limites.maxModulos"));
    }

    @Test
    void sinPlanesCrearResponde403() throws Exception {
        mvc.perform(como(usuarioAgente, post("/api/planes")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Pirata", "1000", 1, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // ---------- HU10 · consultar ----------

    @Test
    void unUsuarioGlobalVeTodosYPuedeFiltrarPorEstado() throws Exception {
        mvc.perform(como(usuarioAgente, get("/api/planes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Antiguo"))
                .andExpect(jsonPath("$.contenido[1].nombre").value("Básico"));
        mvc.perform(como(usuarioAgente, get("/api/planes").param("activo", "false")))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Antiguo"));
    }

    @Test
    void unUsuarioDeEmpresaSoloVePlanesActivosAunqueLosPida() throws Exception {
        mvc.perform(como(usuarioClienteAdmin, get("/api/planes").param("activo", "false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Básico"));
        mvc.perform(como(usuarioClienteAdmin, get("/api/planes/" + antiguo.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        mvc.perform(como(usuarioAgente, get("/api/planes/" + antiguo.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void ordenarPorUnCampoSinIndiceResponde400() throws Exception {
        mvc.perform(como(usuarioAdmin, get("/api/planes").param("orden", "precio,desc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ---------- HU11 · actualizar ----------

    @Test
    void actualizaYDesactivaUnPlan() throws Exception {
        mvc.perform(como(usuarioAdmin, put("/api/planes/" + basico.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Básico 2027", "175000", 2, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Básico 2027"))
                .andExpect(jsonPath("$.precio").value(175000))
                .andExpect(jsonPath("$.activo").value(false));
        assertThat(template.findById(basico.getId(), Plan.class).getActivo()).isFalse();
    }

    @Test
    void renombrarConElNombreDeOtroPlanResponde409() throws Exception {
        mvc.perform(como(usuarioAdmin, put("/api/planes/" + basico.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Antiguo", "150000", 2, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    // ---------- HU12 · eliminar ----------

    @Test
    void unPlanConMembresiasNoSeElimina() throws Exception {
        membresia(basico);
        mvc.perform(como(usuarioAdmin, delete("/api/planes/" + basico.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIENE_DEPENDIENTES"))
                .andExpect(jsonPath("$.dependientes.membresias").value(1));
        assertThat(template.findById(basico.getId(), Plan.class)).isNotNull();
    }

    @Test
    void unPlanSinMembresiasSeEliminaYLuegoResponde404() throws Exception {
        mvc.perform(como(usuarioAdmin, delete("/api/planes/" + antiguo.getId())))
                .andExpect(status().isNoContent());
        mvc.perform(como(usuarioAdmin, get("/api/planes/" + antiguo.getId())))
                .andExpect(status().isNotFound());
    }
}
