package co.edu.ufpso.helpdesk.conocimiento.mapper;

import java.util.Map;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloResponse;
import co.edu.ufpso.helpdesk.conocimiento.dto.ArticuloResumenResponse;

/** Documento {@link Articulo} a DTO (tarea 2.7). La API nunca expone el documento ni el vector. */
public final class ArticuloMapper {

    public static final String USUARIO_ELIMINADO = "Usuario eliminado";

    private ArticuloMapper() {
    }

    /**
     * @param contexto autores, modelo vigente y si el actor puede ver los ids de los tickets vinculados
     */
    public static ArticuloResponse aRespuesta(Articulo a, ContextoArticulos contexto) {
        return new ArticuloResponse(a.getId().toHexString(), a.getTitulo(), a.getContenido(), a.getModulo().name(),
                a.getEtiquetas(), a.getVisibilidad().name(), hex(a.getAutorId()), autor(a, contexto.autores()),
                a.getTicketIds().size(),
                contexto.conTicketIds() ? a.getTicketIds().stream().map(ObjectId::toHexString).toList() : null,
                !vectorVigente(a, contexto.modeloVigente()), a.getFechaCreacion(), a.getFechaActualizacion());
    }

    public static ArticuloResumenResponse aResumen(Articulo a, ContextoArticulos contexto) {
        return new ArticuloResumenResponse(a.getId().toHexString(), a.getTitulo(), a.getModulo().name(),
                a.getEtiquetas(), a.getVisibilidad().name(), autor(a, contexto.autores()), a.getTicketIds().size(),
                !vectorVigente(a, contexto.modeloVigente()), a.getFechaCreacion(), a.getFechaActualizacion());
    }

    /**
     * Un vector cuenta solo si lo generó el modelo vigente. Los 20 vectores sintéticos de la semilla
     * ({@code sintetico/768}) no representan el texto y se regeneran (contexto 6.4).
     */
    public static boolean vectorVigente(Articulo a, String modeloVigente) {
        return modeloVigente.equals(a.getEmbeddingModelo());
    }

    private static String autor(Articulo a, Map<ObjectId, String> autores) {
        return a.getAutorId() == null ? null : autores.getOrDefault(a.getAutorId(), USUARIO_ELIMINADO);
    }

    private static String hex(ObjectId id) {
        return id == null ? null : id.toHexString();
    }

    /**
     * Lo que el mapper necesita además del documento.
     *
     * @param autores       {@code _id → nombre}; un id ausente es un usuario eliminado
     * @param modeloVigente valor de {@code embeddingModelo} que cuenta como vector generado
     * @param conTicketIds  true si el actor es personal de soporte con {@code TICKETS_VER}
     */
    public record ContextoArticulos(Map<ObjectId, String> autores, String modeloVigente, boolean conTicketIds) {

        public ContextoArticulos {
            autores = Map.copyOf(autores);
        }
    }
}
