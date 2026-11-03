package co.edu.ufpso.helpdesk.conocimiento.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;
import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.domain.Visibilidad;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloRequest;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloResponse;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloResumenResponse;
import co.edu.ufpso.helpdesk.conocimiento.mapper.ArticuloMapper;
import co.edu.ufpso.helpdesk.conocimiento.mapper.ArticuloMapper.ContextoArticulos;
import co.edu.ufpso.helpdesk.conocimiento.repository.ArticuloRepository;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;
import co.edu.ufpso.helpdesk.tickets.service.TicketsExistentesService;
import co.edu.ufpso.helpdesk.usuarios.service.NombresUsuarioService;

/**
 * Base de conocimientos (HU29 a HU32, modelo 1.2 sección 9, tarea 2.7). Las búsquedas viven en
 * {@link BusquedaArticulosService}.
 *
 * <p>Visibilidad (P22): sin {@code ARTICULOS_VER_INTERNOS}, el listado y el detalle solo muestran artículos
 * PUBLICO y uno interno responde 404, igual que uno que no existe. Crear, editar, vincular y eliminar no
 * filtran por visibilidad (matriz 2.4: "Sin filtro").
 *
 * <p>Vector (modelo 1.2, "Embeddings"): se genera con Gemini al crear (HU29) y cuando cambian el título, las
 * etiquetas o el contenido (HU31). Las etiquetas también cuentan porque forman parte del texto del vector.
 * Si Gemini falla, el artículo se guarda igual con el vector pendiente y {@link RegeneracionEmbeddings} lo
 * reintenta. Gemini se llama antes de escribir, nunca dentro de una transacción.
 *
 * <p>Tickets vinculados: deben existir (422). HU30 los agrega de a uno con {@code $addToSet}; el tope es 500.
 * Vincular y desvincular cambian {@code fechaActualizacion}, así una edición hecha con una versión anterior
 * responde 409 en lugar de borrar el vínculo nuevo.
 *
 * <p>Eliminar (HU32): borrado físico sin cascada. El vínculo vive solo en el artículo, así que los tickets no
 * cambian. La confirmación la pide el frontend.
 */
@Service
public class ArticuloService {

    private static final Logger LOG = LoggerFactory.getLogger(ArticuloService.class);

    /** Orden permitido en el listado: {@code ix_articulos_modulo_fecha} lo entrega sin ordenar en memoria. */
    private static final Set<String> ORDEN_PERMITIDO = Set.of("fechaCreacion");

    /** Los cinco módulos. Sin filtro de módulo, el listado pide los cinco para que MongoDB use el índice. */
    private static final List<ModuloErp> TODOS_LOS_MODULOS = Arrays.asList(ModuloErp.values());

    private final ArticuloRepository repositorio;
    private final MongoTemplate template;
    private final ProveedorEmbeddings embeddings;
    private final TicketsExistentesService tickets;
    private final NombresUsuarioService nombresUsuarios;
    private final Clock reloj;

    public ArticuloService(ArticuloRepository repositorio, MongoTemplate template, ProveedorEmbeddings embeddings,
            TicketsExistentesService tickets, NombresUsuarioService nombresUsuarios, Clock reloj) {
        this.repositorio = repositorio;
        this.template = template;
        this.embeddings = embeddings;
        this.tickets = tickets;
        this.nombresUsuarios = nombresUsuarios;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // HU29 · Registrar
    // ------------------------------------------------------------------

    public ArticuloResponse crear(ArticuloRequest solicitud, UsuarioAutenticado actor) {
        Instant ahora = ahora();
        Articulo articulo = new Articulo();
        articulo.setFechaCreacion(ahora);
        articulo.setFechaActualizacion(ahora);
        articulo.setAutorId(actor.id());
        aplicar(articulo, solicitud);
        generarVector(articulo);
        verificarCoherencia(articulo);
        return respuesta(repositorio.insert(articulo), actor);
    }

    // ------------------------------------------------------------------
    // HU30 · Consultar
    // ------------------------------------------------------------------

    /**
     * Listado paginado, del más reciente al más antiguo. Ejemplos: {@code ?modulo=INVENTARIOS},
     * {@code ?visibilidad=INTERNO&orden=fechaCreacion,asc}.
     *
     * <p>Usa {@code ix_articulos_modulo_fecha} ({@code modulo: 1, fechaCreacion: -1}). Sin filtro de módulo la
     * consulta lleva {@code modulo: {$in: [los cinco]}}: MongoDB recorre el índice una vez por módulo y mezcla
     * los cinco tramos ya ordenados (etapa {@code SORT_MERGE} en {@code explain}), sin ordenar en memoria.
     *
     * @param visibilidad solo aplica a quien tiene {@code ARTICULOS_VER_INTERNOS}; los demás ven PUBLICO
     */
    public Pagina<ArticuloResumenResponse> listar(ModuloErp modulo, Visibilidad visibilidad, Pageable pageable,
            UsuarioAutenticado actor) {
        Paginacion.validarOrden(pageable, ORDEN_PERMITIDO);
        Pageable conOrden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        Query consulta = new Query(modulo == null
                ? Criteria.where("modulo").in(TODOS_LOS_MODULOS)
                : Criteria.where("modulo").is(modulo));
        Visibilidad filtroVisibilidad = actor.tiene(Permiso.ARTICULOS_VER_INTERNOS) ? visibilidad : Visibilidad.PUBLICO;
        if (filtroVisibilidad != null) {
            consulta.addCriteria(Criteria.where("visibilidad").is(filtroVisibilidad));
        }

        long total = template.count(consulta, Articulo.class);
        // El contenido llega a 50.000 caracteres y el vector a 768 números: el listado no los trae.
        consulta.fields().exclude("contenido").exclude("embedding");
        List<Articulo> articulos = template.find(consulta.with(conOrden), Articulo.class);
        ContextoArticulos contexto = contexto(articulos, actor);
        return Pagina.de(new PageImpl<>(articulos, conOrden, total), a -> ArticuloMapper.aResumen(a, contexto));
    }

    public ArticuloResponse obtener(ObjectId id, UsuarioAutenticado actor) {
        Articulo articulo = buscar(id);
        if (articulo.getVisibilidad() == Visibilidad.INTERNO && !actor.tiene(Permiso.ARTICULOS_VER_INTERNOS)) {
            throw noExiste();
        }
        return respuesta(articulo, actor);
    }

    // ------------------------------------------------------------------
    // HU31 · Actualizar
    // ------------------------------------------------------------------

    /**
     * Reemplaza título, contenido, módulo, etiquetas, visibilidad y tickets. Escribe solo si el artículo no
     * cambió desde que se leyó (misma regla que tickets en 2.6): si otra persona lo cambió en medio, 409.
     */
    public ArticuloResponse actualizar(ObjectId id, ArticuloRequest solicitud, UsuarioAutenticado actor) {
        Articulo articulo = buscar(id);
        Instant leida = articulo.getFechaActualizacion();
        String textoAnterior = TextoEmbedding.de(articulo.getTitulo(), articulo.getEtiquetas(), articulo.getContenido());

        aplicar(articulo, solicitud);
        String textoNuevo = TextoEmbedding.de(articulo.getTitulo(), articulo.getEtiquetas(), articulo.getContenido());
        boolean vectorViejo = !ArticuloMapper.vectorVigente(articulo, embeddings.modelo());
        if (!textoNuevo.equals(textoAnterior) || vectorViejo) {
            generarVector(articulo);
        }
        articulo.setFechaActualizacion(ahora());
        verificarCoherencia(articulo);

        Query mismaVersion = new Query(Criteria.where("_id").is(id).and("fechaActualizacion").is(leida));
        if (template.findAndReplace(mismaVersion, articulo) == null) {
            throw conflicto();
        }
        return respuesta(articulo, actor);
    }

    // ------------------------------------------------------------------
    // HU30 · Vincular el artículo como solución de un ticket
    // ------------------------------------------------------------------

    /**
     * Agrega el ticket con {@code $addToSet}. Si ya estaba vinculado, responde el artículo sin cambios. La
     * condición {@code ticketIds.499} no existe hace cumplir el tope de 500 en la misma escritura.
     */
    public ArticuloResponse vincularTicket(ObjectId id, ObjectId ticketId, UsuarioAutenticado actor) {
        Articulo articulo = buscar(id);
        if (articulo.getTicketIds().contains(ticketId)) {
            return respuesta(articulo, actor);
        }
        if (!tickets.existe(ticketId)) {
            throw new RecursoNoEncontradoException("No existe el ticket solicitado.");
        }
        Query conCupo = new Query(Criteria.where("_id").is(id)
                .and("ticketIds." + (Articulo.MAX_TICKETS - 1)).exists(false));
        Update agregar = new Update().addToSet("ticketIds", ticketId).set("fechaActualizacion", ahora());
        Articulo actualizado = template.findAndModify(conCupo, agregar, FindAndModifyOptions.options().returnNew(true),
                Articulo.class);
        if (actualizado == null) {
            if (!repositorio.existsById(id)) {
                throw noExiste();
            }
            throw new ReglaNegocioException("El artículo ya tiene " + Articulo.MAX_TICKETS
                    + " tickets vinculados, el máximo permitido.");
        }
        return respuesta(actualizado, actor);
    }

    /** Quita el ticket con {@code $pull}. Si no estaba vinculado, responde el artículo sin cambios. */
    public ArticuloResponse desvincularTicket(ObjectId id, ObjectId ticketId, UsuarioAutenticado actor) {
        Articulo articulo = buscar(id);
        if (!articulo.getTicketIds().contains(ticketId)) {
            return respuesta(articulo, actor);
        }
        Update quitar = new Update().pull("ticketIds", ticketId).set("fechaActualizacion", ahora());
        Articulo actualizado = template.findAndModify(new Query(Criteria.where("_id").is(id)), quitar,
                FindAndModifyOptions.options().returnNew(true), Articulo.class);
        if (actualizado == null) {
            throw noExiste();
        }
        return respuesta(actualizado, actor);
    }

    // ------------------------------------------------------------------
    // HU32 · Eliminar
    // ------------------------------------------------------------------

    public void eliminar(ObjectId id) {
        if (!repositorio.existsById(id)) {
            throw noExiste();
        }
        repositorio.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Reglas y utilidades
    // ------------------------------------------------------------------

    /** Pasa la solicitud al documento con los textos sin espacios sobrantes y sin repetidos. */
    private void aplicar(Articulo articulo, ArticuloRequest solicitud) {
        articulo.setTitulo(solicitud.titulo().strip());
        articulo.setContenido(solicitud.contenido().strip());
        articulo.setModulo(solicitud.modulo());
        articulo.setVisibilidad(solicitud.visibilidad() == null ? Visibilidad.INTERNO : solicitud.visibilidad());
        articulo.setEtiquetas(etiquetas(solicitud.etiquetas()));
        articulo.setTicketIds(ticketsExistentes(solicitud.ticketIds()));
    }

    private static List<String> etiquetas(List<String> etiquetas) {
        if (etiquetas == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(etiquetas.stream().map(String::strip).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Los ids sin repetir, en el orden recibido. Si alguno no existe, 422 con los que faltan. */
    private List<ObjectId> ticketsExistentes(List<String> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<ObjectId> pedidos = ticketIds.stream().map(Ids::deCuerpo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<ObjectId> existentes = tickets.existentes(pedidos);
        List<String> faltan = pedidos.stream().filter(t -> !existentes.contains(t)).map(ObjectId::toHexString).toList();
        if (!faltan.isEmpty()) {
            throw new ReglaNegocioException("No existen estos tickets: " + String.join(", ", faltan) + ".");
        }
        return new ArrayList<>(pedidos);
    }

    /**
     * Pide el vector a Gemini. Si falla, el artículo queda con el vector pendiente y se guarda igual: la
     * búsqueda por texto lo encuentra desde ya y la tarea programada lo reintenta.
     */
    private void generarVector(Articulo articulo) {
        if (!embeddings.disponible()) {
            articulo.marcarEmbeddingPendiente();
            return;
        }
        try {
            String texto = TextoEmbedding.de(articulo.getTitulo(), articulo.getEtiquetas(), articulo.getContenido());
            articulo.asignarEmbedding(embeddings.documento(texto), embeddings.modelo());
        } catch (EmbeddingNoDisponibleException e) {
            LOG.info("El artículo se guarda con el vector pendiente ({}).", e.getMotivo());
            articulo.marcarEmbeddingPendiente();
        }
    }

    /** Misma regla del {@code oneOf} del validador: 422 antes de que MongoDB responda 500. */
    private static void verificarCoherencia(Articulo articulo) {
        if (!articulo.embeddingCoherente()) {
            throw new ReglaNegocioException("El vector del artículo no es válido. Intente de nuevo.");
        }
    }

    private Articulo buscar(ObjectId id) {
        return repositorio.findById(id).orElseThrow(ArticuloService::noExiste);
    }

    private ArticuloResponse respuesta(Articulo articulo, UsuarioAutenticado actor) {
        return ArticuloMapper.aRespuesta(articulo, contexto(List.of(articulo), actor));
    }

    /** Una consulta a usuarios para toda la página. */
    private ContextoArticulos contexto(List<Articulo> articulos, UsuarioAutenticado actor) {
        Map<ObjectId, String> autores = nombresUsuarios.nombres(
                articulos.stream().map(Articulo::getAutorId).filter(Objects::nonNull).toList());
        boolean conTicketIds = actor.tiene(Permiso.TICKETS_VER) && !actor.esDeEmpresa();
        return new ContextoArticulos(autores, embeddings.modelo(), conTicketIds);
    }

    private static RecursoNoEncontradoException noExiste() {
        return new RecursoNoEncontradoException("No existe el artículo solicitado.");
    }

    private static ApiException conflicto() {
        return new ApiException(CodigoError.CONFLICTO,
                "Otra persona cambió el artículo mientras usted lo editaba. Recárguelo e intente de nuevo.");
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
