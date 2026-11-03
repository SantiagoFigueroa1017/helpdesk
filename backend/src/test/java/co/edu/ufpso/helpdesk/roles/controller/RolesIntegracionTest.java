package co.edu.ufpso.helpdesk.roles.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * CRUD de roles (HU25 a HU28) de punta a punta: token real, permisos efectivos leídos de MongoDB
 * local y validadores reales de {@code roles} y {@code usuarios} en {@code helpdesk_test} (acta 2.4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolesIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private JwtEncoder encoder;

    private Rol admin;
    private Rol cliente;
    private Usuario usuarioAdmin;
    private Usuario usuarioCliente;

    @BeforeEach
    void prepararDatos() {
        ValidadoresDePrueba.copiar(mongoClient, template, "roles");
        ValidadoresDePrueba.copiar(mongoClient, template, "usuarios");

        admin = rol("ADMIN", AlcanceRol.GLOBAL, true, Arrays.asList(Permiso.values()));
        cliente = rol("CLIENTE", AlcanceRol.EMPRESA, true,
                List.of(Permiso.TICKETS_CREAR, Permiso.TICKETS_VER_PROPIOS, Permiso.ARTICULOS_VER));
        usuarioAdmin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, admin);
        usuarioCliente = usuario("Camilo Quintero", "camilo@ferreteria.co", new ObjectId(), cliente);
    }

    // ---------- utilidades ----------

    private Rol rol(String nombre, AlcanceRol alcance, boolean sistema, List<Permiso> permisos) {
        Instant ahora = Instant.now();
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de RolesIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(true);
        r.setEsSistema(sistema);
        return template.insert(r);
    }

    private Usuario usuario(String nombre, String email, ObjectId empresaId, Rol... roles) {
        Instant ahora = Instant.now();
        Usuario u = new Usuario();
        u.setFechaCreacion(ahora);
        u.setFechaActualizacion(ahora);
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(HASH);
        u.setRolIds(Arrays.stream(roles).map(Rol::getId).toList());
        u.setEmpresaId(empresaId);
        u.setActivo(true);
        return template.insert(u);
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

    private static String cuerpo(String nombre, String alcance, String permisos, Boolean activo) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Descripción de prueba\",\"alcance\":\"" + alcance
                + "\",\"permisos\":[" + permisos + "]" + (activo == null ? "" : ",\"activo\":" + activo) + "}";
    }

    // ---------- HU26 · consultar ----------

    @Test
    void elCatalogoTraeLos49PermisosConGrupoYDescripcion() throws Exception {
        mvc.perform(como(usuarioAdmin, get("/api/permisos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(49)))
                .andExpect(jsonPath("$[0].codigo").value("EMPRESAS_CREAR"))
                .andExpect(jsonPath("$[0].grupo").value("Empresas"))
                .andExpect(jsonPath("$[0].descripcion").value("Registrar empresas clientes"));
    }

    @Test
    void elListadoVaPorNombreYCuentaLosUsuariosDeCadaRol() throws Exception {
        usuario("Natalia Ascanio", "natalia@ferreteria.co", usuarioCliente.getEmpresaId(), cliente);

        mvc.perform(como(usuarioAdmin, get("/api/roles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].nombre").value("ADMIN"))
                .andExpect(jsonPath("$.contenido[0].usuariosAsignados").value(1))
                .andExpect(jsonPath("$.contenido[1].nombre").value("CLIENTE"))
                .andExpect(jsonPath("$.contenido[1].usuariosAsignados").value(2))
                .andExpect(jsonPath("$.contenido[1].permisos[0]").value("TICKETS_CREAR"));
    }

    @Test
    void elListadoFiltraPorAlcanceYRechazaOrdenarPorUnCampoSinIndice() throws Exception {
        mvc.perform(como(usuarioAdmin, get("/api/roles").param("alcance", "EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("CLIENTE"));
        mvc.perform(como(usuarioAdmin, get("/api/roles").param("orden", "descripcion,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void unIdInvalidoOInexistenteResponde404() throws Exception {
        mvc.perform(como(usuarioAdmin, get("/api/roles/no-es-un-id")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        mvc.perform(como(usuarioAdmin, get("/api/roles/" + new ObjectId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinRolesVerResponde403() throws Exception {
        mvc.perform(como(usuarioCliente, get("/api/roles")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    // ---------- HU25 · crear ----------

    @Test
    void creaUnRolActivoQueNoEsDelSistema() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  FACTURACION \",\"descripcion\":\"Registra pagos\",\"alcance\":\"GLOBAL\","
                                + "\"permisos\":[\"PAGOS_VER\",\"PAGOS_REGISTRAR\"],\"esSistema\":true}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/roles/")))
                .andExpect(jsonPath("$.nombre").value("FACTURACION"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.esSistema").value(false))
                .andExpect(jsonPath("$.usuariosAsignados").value(0))
                // En el orden del catálogo, no en el de la petición.
                .andExpect(jsonPath("$.permisos[0]").value("PAGOS_REGISTRAR"))
                .andExpect(jsonPath("$.permisos[1]").value("PAGOS_VER"));
        assertThat(template.count(new org.springframework.data.mongodb.core.query.Query(), Rol.class)).isEqualTo(3);
    }

    @Test
    void unNombreRepetidoResponde409() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("CLIENTE", "EMPRESA", "\"TICKETS_CREAR\"", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void sinPermisosOConUnCodigoFueraDelCatalogoResponde400() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("VACIO", "GLOBAL", "", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("permisos"));
        mvc.perform(como(usuarioAdmin, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("INVENTADO", "GLOBAL", "\"VOLAR\"", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void antiEscaladaNoDejaCrearUnRolConPermisosQueElActorNoTiene() throws Exception {
        Rol gestor = rol("GESTOR_ROLES", AlcanceRol.GLOBAL, false,
                List.of(Permiso.ROLES_VER, Permiso.ROLES_GESTIONAR, Permiso.TICKETS_VER));
        Usuario andres = usuario("Andrés Ropero", "andres@sistemasintegrados.com.co", null, gestor);

        mvc.perform(como(andres, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("SUPER", "GLOBAL", "\"TICKETS_VER\",\"TICKETS_ELIMINAR\"", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.detail").value(containsString("TICKETS_ELIMINAR")));

        // Tampoco puede agregarse permisos editando su propio rol.
        mvc.perform(como(andres, put("/api/roles/" + gestor.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("GESTOR_ROLES", "GLOBAL",
                                "\"ROLES_VER\",\"ROLES_GESTIONAR\",\"TICKETS_VER\",\"USUARIOS_CREAR\"", true)))
                .andExpect(status().isForbidden());

        // Ni tocar un rol más poderoso que él, aunque sea para quitarle permisos.
        mvc.perform(como(andres, delete("/api/roles/" + cliente.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void unActorDeEmpresaNoGestionaRolesGlobales() throws Exception {
        Rol adminEmpresa = rol("ADMIN_EMPRESA_PRUEBA", AlcanceRol.EMPRESA, false,
                List.of(Permiso.ROLES_VER, Permiso.ROLES_GESTIONAR, Permiso.TICKETS_CREAR));
        Usuario laura = usuario("Laura Sanjuán", "laura@ferreteria.co", new ObjectId(), adminEmpresa);

        mvc.perform(como(laura, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("GLOBAL_X", "GLOBAL", "\"TICKETS_CREAR\"", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("EMPRESA")));
        mvc.perform(como(laura, post("/api/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("EMPRESA_X", "EMPRESA", "\"TICKETS_CREAR\"", null)))
                .andExpect(status().isCreated());
    }

    // ---------- HU27 · actualizar ----------

    @Test
    void editarLosPermisosDeUnRolAplicaEnLaSiguientePeticionDeSusUsuarios() throws Exception {
        Rol lector = rol("LECTOR_ROLES", AlcanceRol.GLOBAL, false, List.of(Permiso.ROLES_VER));
        Usuario diego = usuario("Diego Pacheco", "diego@sistemasintegrados.com.co", null, lector);
        mvc.perform(como(diego, get("/api/roles"))).andExpect(status().isOk());

        mvc.perform(como(usuarioAdmin, put("/api/roles/" + lector.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LECTOR_ROLES", "GLOBAL", "\"REPORTES_VER\"", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos[0]").value("REPORTES_VER"))
                .andExpect(jsonPath("$.usuariosAsignados").value(1));

        mvc.perform(como(diego, get("/api/roles")))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarUnRolCortaElAccesoDeQuienSoloTieneEseRol() throws Exception {
        Rol lector = rol("LECTOR_ROLES", AlcanceRol.GLOBAL, false, List.of(Permiso.ROLES_VER));
        Usuario diego = usuario("Diego Pacheco", "diego@sistemasintegrados.com.co", null, lector);

        mvc.perform(como(usuarioAdmin, put("/api/roles/" + lector.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LECTOR_ROLES", "GLOBAL", "\"ROLES_VER\"", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mvc.perform(como(diego, get("/api/roles")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void losRolesDelSistemaConservanNombreYAlcance() throws Exception {
        mvc.perform(como(usuarioAdmin, put("/api/roles/" + cliente.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("CLIENTE_FINAL", "EMPRESA", "\"TICKETS_CREAR\"", true)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    @Test
    void elRolAdminNoPierdeRolesGestionarNiSeDesactiva() throws Exception {
        String todosMenosGestionar = Arrays.stream(Permiso.values()).filter(p -> p != Permiso.ROLES_GESTIONAR)
                .map(p -> "\"" + p.name() + "\"").reduce((a, b) -> a + "," + b).orElseThrow();
        String todos = Arrays.stream(Permiso.values()).map(p -> "\"" + p.name() + "\"")
                .reduce((a, b) -> a + "," + b).orElseThrow();

        mvc.perform(como(usuarioAdmin, put("/api/roles/" + admin.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ADMIN", "GLOBAL", todosMenosGestionar, true)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value(containsString("ROLES_GESTIONAR")));
        mvc.perform(como(usuarioAdmin, put("/api/roles/" + admin.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ADMIN", "GLOBAL", todos, false)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value(containsString("desactivar")));
    }

    @Test
    void elAlcanceNoCambiaSiElRolTieneUsuarios() throws Exception {
        Rol soporte = rol("SOPORTE_N2", AlcanceRol.GLOBAL, false, List.of(Permiso.TICKETS_VER));
        usuario("Yeimy Carrascal", "yeimy@sistemasintegrados.com.co", null, soporte);

        mvc.perform(como(usuarioAdmin, put("/api/roles/" + soporte.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("SOPORTE_N2", "EMPRESA", "\"TICKETS_VER\"", true)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value(containsString("alcance")));
    }

    @Test
    void renombrarAUnNombreQueYaExisteResponde409() throws Exception {
        Rol soporte = rol("SOPORTE_N2", AlcanceRol.GLOBAL, false, List.of(Permiso.TICKETS_VER));

        mvc.perform(como(usuarioAdmin, put("/api/roles/" + soporte.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("CLIENTE", "GLOBAL", "\"TICKETS_VER\"", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    // ---------- HU28 · eliminar ----------

    @Test
    void unRolDelSistemaNoSeElimina() throws Exception {
        mvc.perform(como(usuarioAdmin, delete("/api/roles/" + cliente.getId())))
                .andExpect(status().is(422));
    }

    @Test
    void unRolConUsuariosNoSeEliminaYDiceCuantos() throws Exception {
        Rol soporte = rol("SOPORTE_N2", AlcanceRol.GLOBAL, false, List.of(Permiso.TICKETS_VER));
        usuario("Yeimy Carrascal", "yeimy@sistemasintegrados.com.co", null, soporte);

        mvc.perform(como(usuarioAdmin, delete("/api/roles/" + soporte.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIENE_DEPENDIENTES"))
                .andExpect(jsonPath("$.dependientes.usuarios").value(1));
    }

    @Test
    void unRolSinUsuariosSeEliminaDeVerdad() throws Exception {
        Rol temporal = rol("TEMPORAL", AlcanceRol.GLOBAL, false, List.of(Permiso.REPORTES_VER));

        mvc.perform(como(usuarioAdmin, delete("/api/roles/" + temporal.getId())))
                .andExpect(status().isNoContent());
        assertThat(template.findById(temporal.getId(), Rol.class)).isNull();
        mvc.perform(como(usuarioAdmin, get("/api/roles/" + temporal.getId())))
                .andExpect(status().isNotFound());
    }
}
