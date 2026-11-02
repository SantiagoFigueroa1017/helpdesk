package co.edu.ufpso.helpdesk.common.paginacion;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Respuesta de todo listado de la API (contexto 7.6). Los controladores devuelven esto y
 * nunca un {@link Page} de Spring Data, cuyo JSON no es estable entre versiones.
 *
 * @param contenido      elementos de la página, ya convertidos a DTO
 * @param pagina         número de página, empieza en 0
 * @param tamano         tamaño pedido, después de aplicar el máximo
 * @param totalElementos total de documentos que cumplen el filtro
 * @param totalPaginas   total de páginas con ese tamaño
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    /** Para cuando el repositorio ya devuelve DTO o proyecciones. */
    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Para convertir cada documento a su DTO, por ejemplo {@code Pagina.de(page, mapper::aDto)}. */
    public static <E, T> Pagina<T> de(Page<E> page, Function<? super E, ? extends T> conversor) {
        List<T> contenido = page.getContent().stream().<T>map(conversor).toList();
        return new Pagina<>(contenido, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
