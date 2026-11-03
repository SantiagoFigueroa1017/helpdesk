package co.edu.ufpso.helpdesk.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.auth.domain.RefreshToken;
import co.edu.ufpso.helpdesk.auth.repository.RefreshTokenRepository;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.security.LimiteIntentosLogin;
import co.edu.ufpso.helpdesk.security.PermisosEfectivos;
import co.edu.ufpso.helpdesk.security.PropiedadesJwt;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesion;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioSesionService;

/**
 * Login, renovación y cierre de sesión (contexto 8.3, acta 2.4).
 *
 * <p>Cada sesión es un documento de {@code refreshTokens} con el hash del refresh token. El refresh
 * token rota en cada uso: {@link #renovar} toma y borra el documento en una sola operación
 * atómica e inserta uno nuevo. Si dos peticiones presentan el mismo token, solo una lo encuentra;
 * la otra responde {@code SESION_INVALIDA}.
 *
 * <p>La renovación no va en una transacción a propósito. {@code findAndRemove} ya es atómico, y
 * dentro de una transacción dos renovaciones simultáneas chocarían con un error de escritura
 * (WriteConflict) que saldría como 500. Si la inserción del token nuevo fallara después del
 * borrado, el usuario solo tendría que iniciar sesión otra vez.
 *
 * <p>El access token solo lleva {@code sub} (id del usuario), {@code iat} y {@code exp}. Nunca se
 * registran contraseñas ni tokens.
 */
@Service
public class AuthService {

    private final UsuarioSesionService usuarios;
    private final PermisosEfectivos permisosEfectivos;
    private final RefreshTokenRepository repositorio;
    private final MongoTemplate template;
    private final JwtEncoder jwtEncoder;
    private final PropiedadesJwt propiedades;
    private final Clock reloj;
    private final LimiteIntentosLogin limite;

    public AuthService(UsuarioSesionService usuarios, PermisosEfectivos permisosEfectivos,
            RefreshTokenRepository repositorio, MongoTemplate template, JwtEncoder jwtEncoder,
            PropiedadesJwt propiedades, Clock reloj, LimiteIntentosLogin limite) {
        this.usuarios = usuarios;
        this.permisosEfectivos = permisosEfectivos;
        this.repositorio = repositorio;
        this.template = template;
        this.jwtEncoder = jwtEncoder;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.limite = limite;
    }

    /**
     * Correo o contraseña incorrectos: 401 {@code CREDENCIALES_INVALIDAS}, con el mismo mensaje en
     * los dos casos. Contraseña correcta pero usuario inactivo o sin roles activos: 403
     * {@code CUENTA_INACTIVA}. Correo o IP con demasiados fallos recientes: 429
     * {@code DEMASIADOS_INTENTOS}, sin comprobar la contraseña.
     *
     * @param ip dirección de quien intenta entrar, para el límite por IP
     */
    public SesionEmitida iniciarSesion(String email, String password, String ip) {
        limite.verificar(email, ip);
        UsuarioSesion usuario = usuarios.verificarCredenciales(email, password).orElse(null);
        if (usuario == null) {
            limite.registrarFallo(email, ip);
            throw new ApiException(CodigoError.CREDENCIALES_INVALIDAS);
        }
        limite.registrarExito(email);
        UsuarioAutenticado autenticado = permisosEfectivos.resolver(usuario)
                .orElseThrow(() -> new ApiException(CodigoError.CUENTA_INACTIVA));
        return emitir(autenticado);
    }

    /**
     * Cambia un refresh token vigente por uno nuevo y un access token nuevo. Sin cookie, con un
     * token desconocido, ya usado o vencido: 401 {@code SESION_INVALIDA}. Si el usuario quedó
     * inactivo o sin roles activos: 403 {@code CUENTA_INACTIVA}, y la sesión ya no existe.
     */
    public SesionEmitida renovar(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(CodigoError.SESION_INVALIDA);
        }
        Query porHash = Query.query(Criteria.where("tokenHash").is(TokensRefresh.hash(refreshToken)));
        RefreshToken sesion = template.findAndRemove(porHash, RefreshToken.class);
        if (sesion == null || !sesion.getExpiraEn().isAfter(ahora())) {
            // Vencido pero todavía sin borrar por el índice TTL (corre cada 60 segundos).
            throw new ApiException(CodigoError.SESION_INVALIDA);
        }
        UsuarioAutenticado autenticado = permisosEfectivos.resolver(sesion.getUsuarioId())
                .orElseThrow(() -> new ApiException(CodigoError.CUENTA_INACTIVA));
        return emitir(autenticado);
    }

    /** Borra la sesión si existe. No falla si el token no existe o no llegó: el logout es idempotente. */
    public void cerrarSesion(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            repositorio.deleteByTokenHash(TokensRefresh.hash(refreshToken));
        }
    }

    private SesionEmitida emitir(UsuarioAutenticado usuario) {
        Instant ahora = ahora();
        Instant accessExpira = ahora.plus(propiedades.vidaAccessToken());
        Instant refreshExpira = ahora.plus(propiedades.vidaRefreshToken());

        String refreshToken = TokensRefresh.generar();
        RefreshToken sesion = new RefreshToken();
        sesion.setFechaCreacion(ahora);
        sesion.setFechaActualizacion(ahora);
        sesion.setUsuarioId(usuario.id());
        sesion.setTokenHash(TokensRefresh.hash(refreshToken));
        sesion.setExpiraEn(refreshExpira);
        repositorio.insert(sesion);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.id().toHexString())
                .issuedAt(ahora)
                .expiresAt(accessExpira)
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new SesionEmitida(accessToken, accessExpira, refreshToken, refreshExpira, usuario);
    }

    /** Milisegundos: MongoDB guarda las fechas con esa precisión. */
    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
