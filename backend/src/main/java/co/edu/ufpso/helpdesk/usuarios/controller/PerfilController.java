package co.edu.ufpso.helpdesk.usuarios.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.usuarios.dto.ActualizarPerfilRequest;
import co.edu.ufpso.helpdesk.usuarios.dto.UsuarioResponse;
import co.edu.ufpso.helpdesk.usuarios.service.UsuarioService;
import jakarta.validation.Valid;

/**
 * Perfil propio (HU06 y HU07). Solo exige estar autenticado: el usuario siempre es el del token,
 * nunca un id que llegue en la petición.
 */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final UsuarioService servicio;

    public PerfilController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public UsuarioResponse obtener(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.perfil(actor);
    }

    /** Nombre y, con la contraseña actual, la contraseña. */
    @PutMapping
    public UsuarioResponse actualizar(@Valid @RequestBody ActualizarPerfilRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizarPerfil(solicitud, actor);
    }
}
