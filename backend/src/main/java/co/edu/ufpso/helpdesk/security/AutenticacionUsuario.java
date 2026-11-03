package co.edu.ufpso.helpdesk.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Autenticación de una petición con access token válido (acta 2.4). El principal es el
 * {@link UsuarioAutenticado}; las authorities son sus permisos efectivos, con el código tal cual
 * ({@code TICKETS_CREAR}), para que funcione {@code @PreAuthorize("hasAuthority('TICKETS_CREAR')")}.
 */
public class AutenticacionUsuario extends AbstractAuthenticationToken {

    private final UsuarioAutenticado usuario;
    private final transient Jwt jwt;

    public AutenticacionUsuario(UsuarioAutenticado usuario, Jwt jwt) {
        super(usuario.permisos().stream().map(p -> new SimpleGrantedAuthority(p.name())).toList());
        this.usuario = usuario;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public UsuarioAutenticado getPrincipal() {
        return usuario;
    }

    /** El access token ya validado. No se registra ni se devuelve al cliente. */
    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return usuario.id().toHexString();
    }

    /** Sin el token: {@code toString} puede terminar en un log. */
    @Override
    public String toString() {
        return "AutenticacionUsuario[usuario=" + usuario.id() + ", permisos=" + usuario.permisos().size() + "]";
    }
}
