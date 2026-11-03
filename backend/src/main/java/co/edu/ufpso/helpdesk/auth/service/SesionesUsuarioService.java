package co.edu.ufpso.helpdesk.auth.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.auth.repository.RefreshTokenRepository;

/**
 * Cierra todas las sesiones de un usuario borrando sus refresh tokens (tarea 2.5). Lo usa el módulo
 * de usuarios al desactivarlo, al cambiarle la contraseña y al eliminarlo (HU07, HU08). Los access
 * tokens que ya circulan dejan de servir en la siguiente petición, porque el servicio de usuarios
 * vacía también la caché {@code usuariosSesion}.
 */
@Service
public class SesionesUsuarioService {

    private final RefreshTokenRepository repositorio;

    public SesionesUsuarioService(RefreshTokenRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Usa {@code ix_refresh_usuario}. Devuelve cuántas sesiones cerró. */
    public long cerrarTodas(ObjectId usuarioId) {
        return repositorio.deleteByUsuarioId(usuarioId);
    }
}
