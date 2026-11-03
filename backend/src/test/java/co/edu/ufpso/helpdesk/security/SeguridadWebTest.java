package co.edu.ufpso.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cadena de filtros completa de {@code config.SeguridadConfig}: sin sesión, token Bearer,
 * rutas públicas y 401 y 403 en el mismo ProblemDetail que el resto de la API.
 *
 * <p>Levanta el contexto de Spring como las pruebas de persistencia, pero no escribe en MongoDB.
 * El controlador de prueba solo existe en esta clase ({@code @Import}); Boot no lo registra en
 * otras pruebas porque está anidado en una clase de pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SeguridadWebTest.ControladorDePrueba.class)
class SeguridadWebTest {

    private static final String PROBLEMA_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder encoder;

    @RestController
    @RequestMapping("/api/prueba-seguridad")
    static class ControladorDePrueba {

        @GetMapping("/autenticado")
        Map<String, String> autenticado(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("usuarioId", jwt.getSubject());
        }

        @GetMapping("/con-permiso")
        @PreAuthorize("hasAuthority('ROLES_GESTIONAR')")
        Map<String, String> conPermiso() {
            return Map.of("ok", "si");
        }
    }

    private String tokenValido() {
        Instant ahora = Instant.now();
        return JwtConfigTest.firmar(encoder, ahora, ahora.plusSeconds(900));
    }

    @Test
    void sinTokenResponde401NoAutenticado() throws Exception {
        mvc.perform(get("/api/prueba-seguridad/autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/prueba-seguridad/autenticado"))
                .andExpect(jsonPath("$.fecha").exists());
    }

    @Test
    void tokenMalFormadoResponde401TokenInvalido() throws Exception {
        mvc.perform(get("/api/prueba-seguridad/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA_JSON))
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void tokenFirmadoConOtraClaveResponde401TokenInvalido() throws Exception {
        JwtConfig config = new JwtConfig();
        String otraClave = Base64.getEncoder().encodeToString(new byte[32]);
        Instant ahora = Instant.now();
        String token = JwtConfigTest.firmar(config.jwtEncoder(JwtConfig.claveDesdeBase64(otraClave)),
                ahora, ahora.plusSeconds(900));

        mvc.perform(get("/api/prueba-seguridad/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void tokenVencidoResponde401TokenInvalido() throws Exception {
        Instant hace1Hora = Instant.now().minusSeconds(3600);
        String token = JwtConfigTest.firmar(encoder, hace1Hora, hace1Hora.plusSeconds(900));

        mvc.perform(get("/api/prueba-seguridad/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void tokenValidoEntraYElSujetoEsElUsuario() throws Exception {
        mvc.perform(get("/api/prueba-seguridad/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValido()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value("6750a1b2c3d4e5f601234567"));
    }

    @Test
    void tokenValidoSinElPermisoResponde403AccesoDenegado() throws Exception {
        mvc.perform(get("/api/prueba-seguridad/con-permiso").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValido()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEMA_JSON))
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void lasRutasDeAutenticacionNoPidenToken() throws Exception {
        // Todavía no hay controlador de login. Si la ruta es pública, la petición pasa los filtros
        // y Spring MVC responde que no existe (404, o 405 si la atiende el manejador de archivos
        // estáticos); lo que no puede salir es un 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405))
                .andExpect(jsonPath("$.codigo").value(not("NO_AUTENTICADO")));
    }

    @Test
    void noCreaSesionDeServidor() throws Exception {
        mvc.perform(get("/api/prueba-seguridad/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValido()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
