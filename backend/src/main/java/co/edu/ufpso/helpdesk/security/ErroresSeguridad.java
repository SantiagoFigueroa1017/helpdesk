package co.edu.ufpso.helpdesk.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respuesta de los 401 y 403 que nacen en los filtros de Spring Security (acta 2.4).
 *
 * <p>Esos errores ocurren antes de llegar a un controlador, fuera del alcance del
 * {@code @RestControllerAdvice}. En vez de escribir el JSON a mano, esta clase entrega la
 * excepción al {@link HandlerExceptionResolver} de Spring MVC, que la envía a
 * {@code ManejadorGlobalErrores}. Así hay una sola fuente del formato ProblemDetail.
 */
@Component
public class ErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ErroresSeguridad(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /** 401. La cabecera {@code WWW-Authenticate: Bearer} la pide el RFC 6750. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException excepcion) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        resolver.resolveException(request, response, null, excepcion);
    }

    /** 403: el usuario está autenticado pero le falta el permiso. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException excepcion) {
        resolver.resolveException(request, response, null, excepcion);
    }
}
