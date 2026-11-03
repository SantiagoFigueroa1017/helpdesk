package co.edu.ufpso.helpdesk.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.MongoClient;

import co.edu.ufpso.helpdesk.auth.domain.RefreshToken;
import co.edu.ufpso.helpdesk.auth.service.TokensRefresh;
import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.roles.service.RolSesionService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesionService;
import jakarta.servlet.http.Cookie;

/**
 * Ciclo completo de la sesión contra MongoDB local, con los validadores reales de {@code usuarios},
 * {@code roles} y {@code refreshTokens} copiados a {@code helpdesk_test} (acta 2.4).
 *
 * <p>Cubre: login, cookie del refresh token, rotación, reutilización de un token rotado, logout,
 * permiso efectivo aplicado con {@code @PreAuthorize}, y que un cambio en un rol o en el usuario
 * aplique en la siguiente petición (HU27).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegracionTest.ControladorDePrueba.class)
class AuthIntegracionTest {

    private static final String CLAVE = "Helpdesk2026*";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RolSesionService rolSesionService;

    @Autowired
    private UsuarioSesionService usuarioSesionService;

    private Rol rol;
    private Usuario usuario;

    @RestController
    @RequestMapping("/api/prueba-auth")
    static class ControladorDePrueba {

        @GetMapping("/yo")
        Map<String, Object> yo(@AuthenticationPrincipal UsuarioAutenticado u) {
            return Map.of("id", u.id().toHexString(), "permisos", u.permisos().size());
        }

        @GetMapping("/roles")
        @PreAuthorize("hasAuthority('ROLES_GESTIONAR')")
        Map<String, String> roles() {
            return Map.of("ok", "si");
        }
    }

    @BeforeEach
    void prepararDatos() {
        ValidadoresDePrueba.copiar(mongoClient, template, "roles");
        ValidadoresDePrueba.copiar(mongoClient, template, "usuarios");
        ValidadoresDePrueba.copiar(mongoClient, template, "refreshTokens");

        Instant ahora = Instant.now();
        rol = new Rol();
        rol.setFechaCreacion(ahora);
        rol.setFechaActualizacion(ahora);
        rol.setNombre("ADMIN_PRUEBA");
        rol.setDescripcion("Rol creado por AuthIntegracionTest");
        rol.setAlcance(AlcanceRol.GLOBAL);
        rol.setPermisos(List.of(Permiso.ROLES_VER, Permiso.ROLES_GESTIONAR));
        rol.setActivo(true);
        rol.setEsSistema(false);
        rol = template.insert(rol);

        usuario = new Usuario();
        usuario.setFechaCreacion(ahora);
        usuario.setFechaActualizacion(ahora);
        usuario.setNombre("Yeimy Carrascal");
        usuario.setEmail("yeimy.carrascal@sistemasintegrados.com.co");
        usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
        usuario.setRolIds(List.of(rol.getId()));
        usuario.setEmpresaId(null);
        usuario.setActivo(true);
        usuario = template.insert(usuario);
    }

    private MvcResult login(String email, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}"))
                .andReturn();
    }

    private static String accessToken(MvcResult r) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshToken(MvcResult r) {
        Cookie cookie = r.getResponse().getCookie(CookieRefresh.NOMBRE);
        assertThat(cookie).as("cookie del refresh token").isNotNull();
        return cookie.getValue();
    }

    private long sesionesGuardadas() {
        return template.count(new Query(), RefreshToken.class);
    }

    // ---------- login ----------

    @Test
    void loginCorrectoDevuelveAccessTokenPermisosYCookie() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        // Correo con mayúsculas: se pasa a minúsculas antes de buscar.
                        .content("{\"email\":\"Yeimy.Carrascal@SistemasIntegrados.com.co\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.usuario.id").value(usuario.getId().toHexString()))
                .andExpect(jsonPath("$.usuario.alcance").value("GLOBAL"))
                .andExpect(jsonPath("$.usuario.empresaId").value(nullValue()))
                .andExpect(jsonPath("$.usuario.permisos[0]").value("ROLES_GESTIONAR"))
                .andExpect(jsonPath("$.usuario.permisos[1]").value("ROLES_VER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("HttpOnly"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("Secure"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("SameSite=Strict"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("Path=/api/auth"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("Max-Age=604800"))))
                .andReturn();

        // En la base queda el hash del token de la cookie, nunca el token.
        RefreshToken guardado = template.findOne(new Query(), RefreshToken.class);
        assertThat(guardado).isNotNull();
        assertThat(guardado.getTokenHash()).isEqualTo(TokensRefresh.hash(refreshToken(r)));
        assertThat(guardado.getUsuarioId()).isEqualTo(usuario.getId());
    }

    @Test
    void contrasenaIncorrectaYCorreoInexistenteRespondenLoMismo() throws Exception {
        for (MvcResult r : List.of(login(usuario.getEmail(), "otraClave1*"),
                login("nadie@sistemasintegrados.com.co", CLAVE))) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat((String) JsonPath.read(r.getResponse().getContentAsString(), "$.codigo"))
                    .isEqualTo("CREDENCIALES_INVALIDAS");
            assertThat(r.getResponse().getCookie(CookieRefresh.NOMBRE)).isNull();
        }
        assertThat(sesionesGuardadas()).isZero();
    }

    @Test
    void loginSinCorreoRespondeValidacion() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"));
    }

    @Test
    void usuarioInactivoConLaClaveCorrectaRespondeCuentaInactiva() throws Exception {
        template.updateFirst(Query.query(Criteria.where("_id").is(usuario.getId())),
                Update.update("activo", false), Usuario.class);

        MvcResult r = login(usuario.getEmail(), CLAVE);

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(r.getResponse().getContentAsString(), "$.codigo"))
                .isEqualTo("CUENTA_INACTIVA");
        assertThat(sesionesGuardadas()).isZero();
    }

    // ---------- access token y permisos ----------

    @Test
    void elAccessTokenDelLoginDaLosPermisosDelRol() throws Exception {
        String token = accessToken(login(usuario.getEmail(), CLAVE));

        mvc.perform(get("/api/prueba-auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId().toHexString()))
                .andExpect(jsonPath("$.permisos").value(2));
        mvc.perform(get("/api/prueba-auth/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void quitarUnPermisoAlRolAplicaEnLaSiguientePeticion() throws Exception {
        String token = accessToken(login(usuario.getEmail(), CLAVE));
        mvc.perform(get("/api/prueba-auth/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Lo que hará el CRUD de roles (HU27): guardar el cambio y vaciar la caché del rol.
        template.updateFirst(Query.query(Criteria.where("_id").is(rol.getId())),
                Update.update("permisos", List.of(Permiso.ROLES_VER.name())), Rol.class);
        rolSesionService.invalidar(rol.getId());

        mvc.perform(get("/api/prueba-auth/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void desactivarAlUsuarioCortaSuAccessTokenEnLaSiguientePeticion() throws Exception {
        String token = accessToken(login(usuario.getEmail(), CLAVE));

        template.updateFirst(Query.query(Criteria.where("_id").is(usuario.getId())),
                Update.update("activo", false), Usuario.class);
        usuarioSesionService.invalidarSesion(usuario.getId());

        mvc.perform(get("/api/prueba-auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    // ---------- refresh ----------

    @Test
    void refreshRotaElTokenYElAnteriorYaNoSirve() throws Exception {
        String primero = refreshToken(login(usuario.getEmail(), CLAVE));

        MvcResult renovado = mvc.perform(post("/api/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, primero)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.usuario.id").value(usuario.getId().toHexString()))
                .andReturn();
        String segundo = refreshToken(renovado);

        assertThat(segundo).isNotEqualTo(primero);
        assertThat(sesionesGuardadas()).isEqualTo(1);

        // El token rotado no se puede volver a usar.
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, primero)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));

        // El nuevo sí, y el access token renovado funciona.
        mvc.perform(get("/api/prueba-auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(renovado)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshSinCookieOConTokenDesconocidoRespondeSesionInvalida() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, TokensRefresh.generar())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
    }

    @Test
    void refreshConSesionVencidaRespondeSesionInvalida() throws Exception {
        String token = refreshToken(login(usuario.getEmail(), CLAVE));
        // El índice TTL no existe en helpdesk_test; se simula un documento vencido sin borrar.
        template.updateFirst(new Query(), Update.update("expiraEn", Instant.now().minusSeconds(60)), RefreshToken.class);

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
    }

    @Test
    void refreshDeUnUsuarioDesactivadoRespondeCuentaInactivaYBorraLaSesion() throws Exception {
        String token = refreshToken(login(usuario.getEmail(), CLAVE));
        template.updateFirst(Query.query(Criteria.where("_id").is(usuario.getId())),
                Update.update("activo", false), Usuario.class);
        usuarioSesionService.invalidarSesion(usuario.getId());

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"));
        assertThat(sesionesGuardadas()).isZero();
    }

    // ---------- logout ----------

    @Test
    void logoutBorraLaSesionYLaCookie() throws Exception {
        String token = refreshToken(login(usuario.getEmail(), CLAVE));

        mvc.perform(post("/api/auth/logout").cookie(new Cookie(CookieRefresh.NOMBRE, token)))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString("Max-Age=0"))));
        assertThat(sesionesGuardadas()).isZero();

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
    }

    @Test
    void logoutSinCookieTambienResponde204() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void cadaLoginCreaUnaSesionAparte() throws Exception {
        login(usuario.getEmail(), CLAVE);
        login(usuario.getEmail(), CLAVE);
        assertThat(sesionesGuardadas()).isEqualTo(2);
        assertThat(template.count(Query.query(Criteria.where("usuarioId").is(usuario.getId())), RefreshToken.class))
                .isEqualTo(2);
    }
}
