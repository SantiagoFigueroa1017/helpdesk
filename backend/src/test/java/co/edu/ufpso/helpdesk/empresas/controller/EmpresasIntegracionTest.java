package co.edu.ufpso.helpdesk.empresas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
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
import co.edu.ufpso.helpdesk.empresas.domain.Contacto;
import co.edu.ufpso.helpdesk.empresas.domain.Empresa;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * CRUD de empresas (HU01 a HU04) de punta a punta, con token real y los validadores reales de las
 * colecciones que bloquean el borrado (tarea 2.5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmpresasIntegracionTest {

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
    private Empresa ferreteria;
    private Empresa panaderia;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "membresias", "pagos", "tickets",
                "capacitaciones", "solicitudesCapacitacion")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ferreteria = empresa("900123456-1", "Ferretería El Tornillo S.A.S.");
        panaderia = empresa("800765432-9", "Panadería La Espiga");

        Rol admin = rol("ADMIN", AlcanceRol.GLOBAL, Arrays.asList(Permiso.values()));
        Rol agente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.EMPRESAS_VER));
        Rol clienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA,
                List.of(Permiso.EMPRESAS_VER, Permiso.EMPRESAS_EDITAR_CONTACTO));
        usuarioAdmin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, admin);
        usuarioAgente = usuario("Jhon Pacheco", "jhon@sistemasintegrados.com.co", null, agente);
        usuarioClienteAdmin = usuario("Camilo Quintero", "camilo@ferreteria.co", ferreteria.getId(), clienteAdmin);
    }

    // ---------- utilidades ----------

    private Empresa empresa(String nit, String razonSocial) {
        Instant ahora = Instant.now();
        Empresa e = new Empresa();
        e.setFechaCreacion(ahora);
        e.setFechaActualizacion(ahora);
        e.setNit(nit);
        e.setRazonSocial(razonSocial);
        e.setContacto(new Contacto("Contacto de prueba", "contacto@empresa.co", "3001234567", null, "Ocaña"));
        return template.insert(e);
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Instant ahora = Instant.now();
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de EmpresasIntegracionTest");
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

    private void membresia(ObjectId empresaId) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(empresaId);
        m.setPlanId(new ObjectId());
        m.setModulosContratados(List.of(ModuloErp.COMPRAS));
        m.setFechaInicio(ahora);
        m.setFechaVencimiento(ahora.plus(30, ChronoUnit.DAYS));
        m.setEstado(EstadoMembresia.PENDIENTE);
        template.insert(m);
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

    private static String contacto(String direccion) {
        return "{\"nombre\":\"Marta Pérez\",\"email\":\" Marta.Perez@Tornillo.co \",\"telefono\":\"(607) 562 1234\""
                + (direccion == null ? "" : ",\"direccion\":\"" + direccion + "\"") + "}";
    }

    private static String cuerpo(String nit, String razonSocial) {
        return "{\"nit\":\"" + nit + "\",\"razonSocial\":\"" + razonSocial + "\",\"contacto\":" + contacto("Calle 10 # 12-30")
                + "}";
    }

    // ---------- HU01 · crear ----------

    @Test
    void creaUnaEmpresaConElCorreoDelContactoEnMinusculas() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/empresas")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("901222333-4", "  Lácteos del Norte ")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/empresas/")))
                .andExpect(jsonPath("$.razonSocial").value("Lácteos del Norte"))
                .andExpect(jsonPath("$.contacto.email").value("marta.perez@tornillo.co"))
                .andExpect(jsonPath("$.contacto.ciudad").isEmpty());
    }

    @Test
    void unNitRepetidoResponde409YUnoMalFormado400() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/empresas")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("900123456-1", "Otra ferretería")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        mvc.perform(como(usuarioAdmin, post("/api/empresas")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("900123456", "Sin dígito")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nit"));
    }

    // ---------- HU02 · consultar ----------

    @Test
    void unUsuarioGlobalListaYBuscaPorElComienzoDeLaRazonSocial() throws Exception {
        mvc.perform(como(usuarioAgente, get("/api/empresas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].razonSocial").value("Ferretería El Tornillo S.A.S."));
        mvc.perform(como(usuarioAgente, get("/api/empresas").param("texto", "pana")))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nit").value("800765432-9"));
    }

    @Test
    void unUsuarioDeEmpresaSoloVeLaSuya() throws Exception {
        mvc.perform(como(usuarioClienteAdmin, get("/api/empresas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(ferreteria.getId().toHexString()));
        mvc.perform(como(usuarioClienteAdmin, get("/api/empresas/" + panaderia.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ---------- HU03 · actualizar ----------

    @Test
    void elAdminEditaTodoYElClienteAdminNoPuede() throws Exception {
        mvc.perform(como(usuarioAdmin, put("/api/empresas/" + panaderia.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("800765432-1", "Panadería La Espiga S.A.S.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("800765432-1"));
        mvc.perform(como(usuarioClienteAdmin, put("/api/empresas/" + ferreteria.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("900123456-1", "Me cambio el nombre")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elClienteAdminEditaElContactoDeSuEmpresaYNoElDeOtra() throws Exception {
        mvc.perform(como(usuarioClienteAdmin, put("/api/empresas/" + ferreteria.getId() + "/contacto"))
                        .contentType(MediaType.APPLICATION_JSON).content(contacto("   ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacto.nombre").value("Marta Pérez"))
                .andExpect(jsonPath("$.razonSocial").value("Ferretería El Tornillo S.A.S."));
        Document crudo = template.getCollection("empresas").find(new Document("_id", ferreteria.getId())).first();
        assertThat(crudo.get("contacto", Document.class)).doesNotContainKey("direccion");

        mvc.perform(como(usuarioClienteAdmin, put("/api/empresas/" + panaderia.getId() + "/contacto"))
                        .contentType(MediaType.APPLICATION_JSON).content(contacto(null)))
                .andExpect(status().isNotFound());
    }

    // ---------- HU04 · eliminar ----------

    @Test
    void unaEmpresaConDependientesNoSeEliminaYDiceCuantos() throws Exception {
        membresia(ferreteria.getId());
        mvc.perform(como(usuarioAdmin, delete("/api/empresas/" + ferreteria.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIENE_DEPENDIENTES"))
                .andExpect(jsonPath("$.dependientes", aMapWithSize(2)))
                .andExpect(jsonPath("$.dependientes.usuarios").value(1))
                .andExpect(jsonPath("$.dependientes.membresias").value(1));
    }

    @Test
    void unaEmpresaSinDependientesSeElimina() throws Exception {
        mvc.perform(como(usuarioAdmin, delete("/api/empresas/" + panaderia.getId())))
                .andExpect(status().isNoContent());
        assertThat(template.findById(panaderia.getId(), Empresa.class)).isNull();
        mvc.perform(como(usuarioAgente, delete("/api/empresas/" + ferreteria.getId())))
                .andExpect(status().isForbidden());
    }
}
