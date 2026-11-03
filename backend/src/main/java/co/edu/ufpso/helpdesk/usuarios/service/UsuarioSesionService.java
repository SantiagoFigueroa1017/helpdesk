package co.edu.ufpso.helpdesk.usuarios.service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;
import co.edu.ufpso.helpdesk.usuarios.repository.UsuarioRepository;

/**
 * Consultas de usuarios que usan el login y la verificación de cada petición (acta 2.4).
 *
 * <p>{@link #buscarParaSesion} pasa por la caché {@code usuariosSesion}, porque se consulta en cada
 * petición autenticada. Todo servicio que cambie el estado, los roles o la empresa de un usuario
 * debe llamar a {@link #invalidarSesion} en la misma operación (2.5), para que el cambio aplique en
 * la siguiente petición.
 */
@Service
public class UsuarioSesionService {

    public static final String CACHE = "usuariosSesion";

    private final UsuarioRepository repositorio;
    private final PasswordEncoder passwordEncoder;

    /**
     * Hash de una contraseña aleatoria. Si el correo no existe, se compara contra este hash para
     * que la respuesta tarde lo mismo que con un correo real y no revele cuáles existen.
     */
    private final String hashSenuelo;

    public UsuarioSesionService(UsuarioRepository repositorio, PasswordEncoder passwordEncoder) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
        this.hashSenuelo = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Comprueba correo y contraseña. Devuelve vacío si alguno no coincide, sin decir cuál. No
     * revisa si el usuario está activo: eso lo decide quien llama, después de saber que la
     * contraseña es correcta.
     */
    public Optional<UsuarioSesion> verificarCredenciales(String email, String password) {
        if (email == null || password == null) {
            return Optional.empty();
        }
        Optional<Usuario> usuario = repositorio.findByEmail(email.strip().toLowerCase(Locale.ROOT));
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashSenuelo);
        boolean coincide = passwordEncoder.matches(password, hash);
        return coincide ? usuario.map(UsuarioSesionService::aSesion) : Optional.empty();
    }

    @Cacheable(cacheNames = CACHE, key = "#id")
    public Optional<UsuarioSesion> buscarParaSesion(ObjectId id) {
        return repositorio.findById(id).map(UsuarioSesionService::aSesion);
    }

    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void invalidarSesion(ObjectId id) {
        // Solo vacía la entrada de la caché.
    }

    private static UsuarioSesion aSesion(Usuario u) {
        return new UsuarioSesion(u.getId(), u.getNombre(), u.getEmail(), u.getEmpresaId(),
                u.getRolIds(), Boolean.TRUE.equals(u.getActivo()));
    }
}
