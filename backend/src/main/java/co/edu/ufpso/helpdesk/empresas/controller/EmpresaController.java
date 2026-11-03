package co.edu.ufpso.helpdesk.empresas.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.empresas.dto.ActualizarEmpresaRequest;
import co.edu.ufpso.helpdesk.empresas.dto.ContactoRequest;
import co.edu.ufpso.helpdesk.empresas.dto.CrearEmpresaRequest;
import co.edu.ufpso.helpdesk.empresas.dto.EmpresaResponse;
import co.edu.ufpso.helpdesk.empresas.service.EmpresaService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/** Empresas (HU01 a HU04). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}. */
@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private final EmpresaService servicio;

    public EmpresaController(EmpresaService servicio) {
        this.servicio = servicio;
    }

    /** HU02: {@code ?texto=ferre&nit=900123456-1&pagina=0&tamano=20&orden=razonSocial,asc}. */
    @GetMapping
    @PreAuthorize("hasAuthority('EMPRESAS_VER')")
    public Pagina<EmpresaResponse> listar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) String nit, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(texto, nit, pageable, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPRESAS_VER')")
    public EmpresaResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU01. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('EMPRESAS_CREAR')")
    public ResponseEntity<EmpresaResponse> crear(@Valid @RequestBody CrearEmpresaRequest solicitud) {
        EmpresaResponse creada = servicio.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/empresas/" + creada.id())).body(creada);
    }

    /** HU03: edición completa. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPRESAS_EDITAR')")
    public EmpresaResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarEmpresaRequest solicitud) {
        return servicio.actualizar(Ids.deRuta(id), solicitud);
    }

    /** HU03: solo el contacto. El CLIENTE_ADMIN, únicamente el de su empresa. */
    @PutMapping("/{id}/contacto")
    @PreAuthorize("hasAnyAuthority('EMPRESAS_EDITAR','EMPRESAS_EDITAR_CONTACTO')")
    public EmpresaResponse actualizarContacto(@PathVariable String id, @Valid @RequestBody ContactoRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizarContacto(Ids.deRuta(id), solicitud, actor);
    }

    /** HU04. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPRESAS_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        servicio.eliminar(Ids.deRuta(id));
        return ResponseEntity.noContent().build();
    }
}
