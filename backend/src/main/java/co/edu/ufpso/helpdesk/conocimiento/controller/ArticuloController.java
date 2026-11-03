package co.edu.ufpso.helpdesk.conocimiento.controller;

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
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.conocimiento.domain.Visibilidad;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloRequest;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloResponse;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloResumenResponse;
import co.edu.ufpso.helpdesk.conocimiento.dto.ResultadoBusquedaResponse;
import co.edu.ufpso.helpdesk.conocimiento.service.ArticuloService;
import co.edu.ufpso.helpdesk.conocimiento.service.BusquedaArticulosService;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import jakarta.validation.Valid;

/**
 * Base de conocimientos (HU29 a HU32). Permisos según {@code modelo/MATRIZ_PERMISOS_2.4.md}.
 * {@code ARTICULOS_VER_INTERNOS} no protege un endpoint propio: sin él, las cuatro consultas solo ven
 * artículos PUBLICO.
 */
@RestController
@RequestMapping("/api/articulos")
public class ArticuloController {

    private final ArticuloService servicio;
    private final BusquedaArticulosService busqueda;

    public ArticuloController(ArticuloService servicio, BusquedaArticulosService busqueda) {
        this.servicio = servicio;
        this.busqueda = busqueda;
    }

    /** HU30: {@code ?modulo=&visibilidad=&pagina=0&tamano=20&orden=fechaCreacion,desc}. */
    @GetMapping
    @PreAuthorize("hasAuthority('ARTICULOS_VER')")
    public Pagina<ArticuloResumenResponse> listar(@RequestParam(required = false) ModuloErp modulo,
            @RequestParam(required = false) Visibilidad visibilidad, Pageable pageable,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.listar(modulo, visibilidad, pageable, actor);
    }

    /**
     * HU30, búsqueda por palabras: {@code ?texto=datafono no conecta&modulo=VENTAS_EN_SITIO&limite=10}. El
     * frontend la llama tras una pausa de 300 ms.
     */
    @GetMapping("/busqueda")
    @PreAuthorize("hasAuthority('ARTICULOS_VER')")
    public ResultadoBusquedaResponse buscarPorTexto(@RequestParam(required = false) String texto,
            @RequestParam(required = false) ModuloErp modulo, @RequestParam(required = false) Integer limite,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return busqueda.porTexto(texto, modulo, limite, actor);
    }

    /**
     * HU30, búsqueda por significado: {@code ?consulta=el lector no reconoce los productos&modulo=&limite=}. Al
     * menos 3 palabras. El frontend la llama tras una pausa de 800 ms. Si no se puede hacer, responde con la
     * búsqueda por texto y {@code aviso}.
     */
    @GetMapping("/busqueda-semantica")
    @PreAuthorize("hasAuthority('ARTICULOS_VER')")
    public ResultadoBusquedaResponse buscarPorSignificado(@RequestParam(required = false) String consulta,
            @RequestParam(required = false) ModuloErp modulo, @RequestParam(required = false) Integer limite,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return busqueda.porSignificado(consulta, modulo, limite, actor);
    }

    /** HU30: artículo completo. Uno interno responde 404 a quien no tiene {@code ARTICULOS_VER_INTERNOS}. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ARTICULOS_VER')")
    public ArticuloResponse obtener(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.obtener(Ids.deRuta(id), actor);
    }

    /** HU29. Responde 201 con la cabecera {@code Location}. */
    @PostMapping
    @PreAuthorize("hasAuthority('ARTICULOS_CREAR')")
    public ResponseEntity<ArticuloResponse> crear(@Valid @RequestBody ArticuloRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        ArticuloResponse creado = servicio.crear(solicitud, actor);
        return ResponseEntity.created(URI.create("/api/articulos/" + creado.id())).body(creado);
    }

    /** HU31. Reemplaza el artículo completo. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ARTICULOS_EDITAR')")
    public ArticuloResponse actualizar(@PathVariable String id, @Valid @RequestBody ArticuloRequest solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.actualizar(Ids.deRuta(id), solicitud, actor);
    }

    /** HU30: vincula el artículo como solución del ticket. Si ya estaba vinculado, no cambia nada. */
    @PostMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasAuthority('ARTICULOS_EDITAR')")
    public ArticuloResponse vincularTicket(@PathVariable String id, @PathVariable String ticketId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.vincularTicket(Ids.deRuta(id), Ids.deRuta(ticketId), actor);
    }

    /** Quita el vínculo (decidido en 2.7). Si no estaba vinculado, no cambia nada. */
    @DeleteMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasAuthority('ARTICULOS_EDITAR')")
    public ArticuloResponse desvincularTicket(@PathVariable String id, @PathVariable String ticketId,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.desvincularTicket(Ids.deRuta(id), Ids.deRuta(ticketId), actor);
    }

    /** HU32: borrado físico. Los tickets vinculados no cambian. Responde 204. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ARTICULOS_ELIMINAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        servicio.eliminar(Ids.deRuta(id));
        return ResponseEntity.noContent().build();
    }
}
