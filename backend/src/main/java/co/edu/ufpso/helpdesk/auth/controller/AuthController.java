package co.edu.ufpso.helpdesk.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.auth.dto.LoginRequest;
import co.edu.ufpso.helpdesk.auth.dto.SesionResponse;
import co.edu.ufpso.helpdesk.auth.service.AuthService;
import co.edu.ufpso.helpdesk.auth.service.SesionEmitida;
import jakarta.validation.Valid;

/**
 * Endpoints públicos de autenticación (contexto 8.3, acta 2.4). Login y JWT no son historias de las
 * 40; se cubren como requisitos de seguridad del backend.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login}: correo y contraseña. Responde el access token y los datos del
 *       usuario en el cuerpo, y el refresh token en la cookie.</li>
 *   <li>{@code POST /api/auth/refresh}: lee la cookie, rota el refresh token y responde igual que el
 *       login. El frontend lo llama al recargar la página y cuando recibe un 401.</li>
 *   <li>{@code POST /api/auth/logout}: borra la sesión y la cookie. Responde 204.</li>
 * </ul>
 *
 * <p>Las respuestas con tokens llevan {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService servicio;
    private final CookieRefresh cookie;

    public AuthController(AuthService servicio, CookieRefresh cookie) {
        this.servicio = servicio;
        this.cookie = cookie;
    }

    @PostMapping("/login")
    public ResponseEntity<SesionResponse> login(@Valid @RequestBody LoginRequest solicitud) {
        return responder(servicio.iniciarSesion(solicitud.email(), solicitud.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SesionResponse> refresh(
            @CookieValue(name = CookieRefresh.NOMBRE, required = false) String refreshToken) {
        return responder(servicio.renovar(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieRefresh.NOMBRE, required = false) String refreshToken) {
        servicio.cerrarSesion(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.borrar().toString())
                .build();
    }

    private ResponseEntity<SesionResponse> responder(SesionEmitida sesion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.crear(sesion.refreshToken()).toString())
                .body(SesionResponse.de(sesion));
    }
}
