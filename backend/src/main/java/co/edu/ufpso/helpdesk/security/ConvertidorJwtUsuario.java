package co.edu.ufpso.helpdesk.security;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Convierte un access token ya validado (firma y {@code exp}) en la autenticación de la petición
 * (acta 2.4). El token solo trae el id del usuario en {@code sub}; los permisos salen de
 * {@link PermisosEfectivos} en cada petición, así que un cambio en un rol o en el usuario aplica
 * en la siguiente (HU27).
 *
 * <ul>
 *   <li>{@code sub} que no es un ObjectId: 401 {@code TOKEN_INVALIDO}.</li>
 *   <li>Usuario borrado, inactivo o sin roles activos: 401 {@code NO_AUTENTICADO}. El frontend
 *       intenta renovar y {@code /refresh} responde {@code CUENTA_INACTIVA} o {@code SESION_INVALIDA}.</li>
 * </ul>
 */
@Component
public class ConvertidorJwtUsuario implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PermisosEfectivos permisosEfectivos;

    public ConvertidorJwtUsuario(PermisosEfectivos permisosEfectivos) {
        this.permisosEfectivos = permisosEfectivos;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sujeto = jwt.getSubject();
        if (sujeto == null || !ObjectId.isValid(sujeto)) {
            throw new InvalidBearerTokenException("El token no identifica a un usuario");
        }
        UsuarioAutenticado usuario = permisosEfectivos.resolver(new ObjectId(sujeto))
                .orElseThrow(() -> new DisabledException("Usuario sin acceso al sistema"));
        return new AutenticacionUsuario(usuario, jwt);
    }
}
