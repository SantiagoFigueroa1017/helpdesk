package co.edu.ufpso.helpdesk.usuarios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mongodb.client.MongoClient;

import co.edu.ufpso.helpdesk.auth.domain.RefreshToken;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
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
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Usuarios (HU05 a HU08), perfil y roles asignables de punta a punta, con token real y los
 * validadores reales de las colecciones que toca el borrado (tarea 2.5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuariosIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String CLAVE = "Helpdesk2026*";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Instant ahora;
    private Empresa ferreteria;
    private Empresa panaderia;
    private Rol rolAdmin;
    private Rol rolClienteAdmin;
    private Rol rolCliente;
    private Rol rolSupervisor;
    private Usuario admin;
    private Usuario clienteAdmin;
    private Usuario cliente;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "planes", "membresias", "tickets",
                "capacitaciones", "inscripciones", "refreshTokens")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ferreteria = empresa("900123456-1", "Ferretería El Tornillo");
        panaderia = empresa("800765432-9", "Panadería La Espiga");
        membresia(ferreteria.getId(), plan("Básico", 3));

        rolAdmin = rol("ADMIN", AlcanceRol.GLOBAL, Arrays.asList(Permiso.values()), true);
        rolClienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA, List.of(Permiso.USUARIOS_CREAR,
                Permiso.USUARIOS_VER, Permiso.USUARIOS_EDITAR, Permiso.USUARIOS_ELIMINAR, Permiso.TICKETS_CREAR,
                Permiso.TICKETS_VER), true);
        rolCliente = rol("CLIENTE", AlcanceRol.EMPRESA, List.of(Permiso.TICKETS_CREAR, Permiso.TICKETS_VER_PROPIOS),
                true);
        rolSupervisor = rol("SUPERVISOR", AlcanceRol.EMPRESA, List.of(Permiso.TICKETS_VER, Permiso.TICKETS_ELIMINAR),
                true);

        admin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, rolAdmin);
        clienteAdmin = usuario("Camilo Quintero", "camilo@ferreteria.co", ferreteria.getId(), rolClienteAdmin);
        cliente = usuario("Natalia Ascanio", "natalia@ferreteria.co", ferreteria.getId(), rolCliente);
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

    private Plan plan(String nombre, int maxUsuarios) {
        Plan p = new Plan();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setNombre(nombre);
        p.setDescripcion("Plan de prueba");
        p.setPrecio(new BigDecimal("150000"));
        p.setNivelAcceso(NivelAcceso.BASICO);
        p.setLimites(new LimitesPlan(48, 1, maxUsuarios, 2));
        p.setActivo(true);
        return template.insert(p);
    }

    private void membresia(ObjectId empresaId, Plan plan) {
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(empresaId);
        m.setPlanId(plan.getId());
        m.setModulosContratados(List.of(ModuloErp.COMPRAS));
        m.setFechaInicio(ahora.minus(10, ChronoUnit.DAYS));
        m.setFechaVencimiento(ahora.plus(20, ChronoUnit.DAYS));
        m.setEstado(EstadoMembresia.ACTIVA);
        template.insert(m);
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos, boolean activo) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de UsuariosIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(activo);
        r.setEsSistema(false);
        return template.insert(r);
    }

    private Usuario usuario(String nombre, String email, ObjectId empresaId, Rol rol) {
        Usuario u = new Usuario();
        u.setFechaCreacion(ahora);
        u.setFechaActualizacion(ahora);
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(CLAVE));
        u.setRolIds(List.of(rol.getId()));
        u.setEmpresaId(empresaId);
        u.setActivo(true);
        return template.insert(u);
    }

    private void sesion(Usuario u) {
        RefreshToken t = new RefreshToken();
        t.setFechaCreacion(ahora);
        t.setFechaActualizacion(ahora);
        t.setUsuarioId(u.getId());
        t.setTokenHash(String.format("%064x", new java.math.BigInteger(1, u.getId().toByteArray())));
        t.setExpiraEn(ahora.plus(7, ChronoUnit.DAYS));
        template.insert(t);
    }

    private long sesionesDe(Usuario u) {
        return template.count(new Query(Criteria.where("usuarioId").is(u.getId())), RefreshToken.class);
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

    private static String cuerpoCrear(String email, String empresaId, Rol... roles) {
        String ids = String.join(",", Arrays.stream(roles).map(r -> "\"" + r.getId() + "\"").toList());
        return "{\"nombre\":\"Usuario Nuevo\",\"email\":\"" + email + "\",\"password\":\"" + CLAVE
                + "\",\"rolIds\":[" + ids + "]" + (empresaId == null ? "" : ",\"empresaId\":\"" + empresaId + "\"")
                + "}";
    }

    private static String cuerpoEditar(Usuario u, boolean activo, String nuevaPassword, Rol... roles) {
        String ids = String.join(",", Arrays.stream(roles).map(r -> "\"" + r.getId() + "\"").toList());
        return "{\"nombre\":\"" + u.getNombre() + "\",\"email\":\"" + u.getEmail() + "\",\"rolIds\":[" + ids
                + "],\"activo\":" + activo
                + (nuevaPassword == null ? "" : ",\"nuevaPassword\":\"" + nuevaPassword + "\"") + "}";
    }

    // ---------- HU05 · crear ----------

    @Test
    void elClienteAdminCreaUsuariosSoloEnSuEmpresaYSinExponerLaContrasena() throws Exception {
        mvc.perform(como(clienteAdmin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear(" Andrea.Rojas@Ferreteria.co ", panaderia.getId().toHexString(),
                                rolCliente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("andrea.rojas@ferreteria.co"))
                .andExpect(jsonPath("$.empresaId").value(ferreteria.getId().toHexString()))
                .andExpect(jsonPath("$.roles[0].nombre").value("CLIENTE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        Document crudo = template.getCollection("usuarios").find(new Document("email", "andrea.rojas@ferreteria.co"))
                .first();
        assertThat(passwordEncoder.matches(CLAVE, crudo.getString("passwordHash"))).isTrue();
    }

    @Test
    void antiEscaladaNoDejaAsignarRolesConMasPermisosNiGlobales() throws Exception {
        mvc.perform(como(clienteAdmin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("x@ferreteria.co", null, rolSupervisor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("TICKETS_ELIMINAR")));
        mvc.perform(como(clienteAdmin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("y@ferreteria.co", null, rolAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("EMPRESA")));
    }

    @Test
    void elLimiteDeUsuariosDelPlanSeRespeta() throws Exception {
        // El plan permite 3 activos y la ferretería ya tiene 2.
        mvc.perform(como(clienteAdmin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("tercero@ferreteria.co", null, rolCliente)))
                .andExpect(status().isCreated());
        mvc.perform(como(clienteAdmin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("cuarto@ferreteria.co", null, rolCliente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("como máximo 3")));
    }

    @Test
    void sinMembresiaNoSeRegistranUsuariosDeEmpresa() throws Exception {
        mvc.perform(como(admin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("ana@panaderia.co", panaderia.getId().toHexString(), rolCliente)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("membresía")));
    }

    @Test
    void losRolesDebenExistirEstarActivosYCompartirAlcanceConLaEmpresaCorrecta() throws Exception {
        Rol inactivo = rol("ANTIGUO", AlcanceRol.EMPRESA, List.of(Permiso.TICKETS_CREAR), false);
        String ferre = ferreteria.getId().toHexString();
        mvc.perform(como(admin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("a@ferreteria.co", ferre, rolCliente, rolAdmin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("mismo alcance")));
        mvc.perform(como(admin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("b@ferreteria.co", ferre, inactivo)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("inactivo")));
        mvc.perform(como(admin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("c@ferreteria.co", null, rolCliente)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(como(admin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("d@sistemasintegrados.com.co", ferre, rolAdmin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("GLOBAL")));
    }

    @Test
    void unCorreoRepetidoResponde409() throws Exception {
        mvc.perform(como(admin, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCrear("NATALIA@ferreteria.co", ferreteria.getId().toHexString(), rolCliente)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    // ---------- HU06 · consultar y roles asignables ----------

    @Test
    void elClienteAdminSoloVeUsuariosDeSuEmpresa() throws Exception {
        mvc.perform(como(clienteAdmin, get("/api/usuarios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Camilo Quintero"));
        mvc.perform(como(clienteAdmin, get("/api/usuarios/" + admin.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(como(admin, get("/api/usuarios").param("texto", "nata")))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void losRolesAsignablesSonLosContenidosEnLosPermisosDelActor() throws Exception {
        mvc.perform(como(clienteAdmin, get("/api/roles/asignables").param("alcance", "GLOBAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("CLIENTE"))
                .andExpect(jsonPath("$[1].nombre").value("CLIENTE_ADMIN"));
        mvc.perform(como(admin, get("/api/roles/asignables").param("alcance", "GLOBAL")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("ADMIN"));
        mvc.perform(como(cliente, get("/api/roles/asignables")))
                .andExpect(status().isForbidden());
    }

    // ---------- HU07 · actualizar ----------

    @Test
    void desactivarCierraLasSesionesYElTokenDejaDeServir() throws Exception {
        sesion(cliente);
        mvc.perform(como(cliente, get("/api/perfil"))).andExpect(status().isOk());

        mvc.perform(como(clienteAdmin, put("/api/usuarios/" + cliente.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(cliente, false, null, rolCliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        assertThat(sesionesDe(cliente)).isZero();
        mvc.perform(como(cliente, get("/api/perfil"))).andExpect(status().isUnauthorized());
    }

    @Test
    void noSeEditaAUnUsuarioConMasPermisosNiSeDesactivaUnoMismo() throws Exception {
        Usuario supervisor = usuario("Diego Pacheco", "diego@ferreteria.co", ferreteria.getId(), rolSupervisor);
        mvc.perform(como(clienteAdmin, put("/api/usuarios/" + supervisor.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoEditar(supervisor, true, null, rolCliente)))
                .andExpect(status().isForbidden());
        mvc.perform(como(clienteAdmin, put("/api/usuarios/" + clienteAdmin.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(clienteAdmin, false, null, rolClienteAdmin)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void reactivarPorEncimaDelLimiteResponde422() throws Exception {
        usuario("Tercero Activo", "tercero@ferreteria.co", ferreteria.getId(), rolCliente);
        Usuario inactivo = usuario("Cuarto Inactivo", "cuarto@ferreteria.co", ferreteria.getId(), rolCliente);
        template.getCollection("usuarios").updateOne(new Document("_id", inactivo.getId()),
                new Document("$set", new Document("activo", false)));

        mvc.perform(como(clienteAdmin, put("/api/usuarios/" + inactivo.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEditar(inactivo, true, null, rolCliente)))
                .andExpect(status().isUnprocessableContent());
    }

    // ---------- HU08 · eliminar ----------

    @Test
    void unUsuarioConTicketsNoSeEliminaYNadieSeEliminaASiMismo() throws Exception {
        Ticket t = new Ticket();
        t.setFechaCreacion(ahora);
        t.setFechaActualizacion(ahora);
        t.setEmpresaId(ferreteria.getId());
        t.setCreadorId(cliente.getId());
        t.setSolicitanteId(cliente.getId());
        t.setModulo(ModuloErp.COMPRAS);
        t.setTipo(TipoTicket.SUGERENCIA);
        t.setTitulo("Exportar órdenes a Excel");
        t.setDescripcion("Sería útil exportar las órdenes de compra.");
        t.setEstado(EstadoTicket.ABIERTO);
        t.getHistorialEstados().add(new CambioEstado(EstadoTicket.ABIERTO, ahora, cliente.getId()));
        t.setFechaLimiteRespuesta(ahora.plus(48, ChronoUnit.HOURS));
        template.insert(t);

        mvc.perform(como(clienteAdmin, delete("/api/usuarios/" + cliente.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.dependientes.tickets").value(1));
        mvc.perform(como(clienteAdmin, delete("/api/usuarios/" + clienteAdmin.getId())))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void elBorradoCierraSesionesLiberaCuposYBorraInscripcionesEnUnaTransaccion() throws Exception {
        sesion(cliente);
        Capacitacion futura = new Capacitacion();
        futura.setFechaCreacion(ahora);
        futura.setFechaActualizacion(ahora);
        futura.setTitulo("Órdenes de compra paso a paso");
        futura.setTipo(TipoCapacitacion.MASIVA);
        futura.setModulo(ModuloErp.COMPRAS);
        futura.setFechaInicio(ahora.plus(5, ChronoUnit.DAYS));
        futura.setDuracionMinutos(60);
        futura.setCupo(20);
        futura.setCuposOcupados(1);
        futura.setInstructorId(admin.getId());
        futura = template.insert(futura);

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setFechaCreacion(ahora);
        inscripcion.setFechaActualizacion(ahora);
        inscripcion.setCapacitacionId(futura.getId());
        inscripcion.setUsuarioId(cliente.getId());
        inscripcion.setEmpresaId(ferreteria.getId());
        template.insert(inscripcion);

        mvc.perform(como(clienteAdmin, delete("/api/usuarios/" + cliente.getId())))
                .andExpect(status().isNoContent());

        assertThat(template.findById(cliente.getId(), Usuario.class)).isNull();
        assertThat(sesionesDe(cliente)).isZero();
        assertThat(template.count(new Query(), Inscripcion.class)).isZero();
        assertThat(template.findById(futura.getId(), Capacitacion.class).getCuposOcupados()).isZero();

        // El instructor de una capacitación futura no se puede borrar.
        mvc.perform(como(admin, delete("/api/usuarios/" + admin.getId())))
                .andExpect(status().isUnprocessableContent());
        Usuario otroAdmin = usuario("Andrés Ropero", "andres@sistemasintegrados.com.co", null, rolAdmin);
        template.getCollection("capacitaciones").updateOne(new Document("_id", futura.getId()),
                new Document("$set", new Document("instructorId", otroAdmin.getId())));
        mvc.perform(como(admin, delete("/api/usuarios/" + otroAdmin.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.dependientes.capacitaciones").value(1));
    }

    // ---------- Perfil ----------

    @Test
    void elPerfilCambiaElNombreYLaContrasenaSoloConLaActual() throws Exception {
        sesion(cliente);
        mvc.perform(como(cliente, put("/api/perfil")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Natalia A.\",\"passwordActual\":\"equivocada\","
                                + "\"nuevaPassword\":\"OtraClave2026*\"}"))
                .andExpect(status().isUnprocessableContent());
        assertThat(sesionesDe(cliente)).isEqualTo(1);

        mvc.perform(como(cliente, put("/api/perfil")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Natalia A.\",\"passwordActual\":\"" + CLAVE
                                + "\",\"nuevaPassword\":\"OtraClave2026*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Natalia A."))
                .andExpect(jsonPath("$.email").value("natalia@ferreteria.co"));

        Usuario leido = template.findById(cliente.getId(), Usuario.class);
        assertThat(passwordEncoder.matches("OtraClave2026*", leido.getPasswordHash())).isTrue();
        assertThat(sesionesDe(cliente)).isZero();
    }
}
