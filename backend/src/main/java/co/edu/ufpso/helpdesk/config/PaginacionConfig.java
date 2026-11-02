package co.edu.ufpso.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.data.web.config.SortHandlerMethodArgumentResolverCustomizer;

import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;

/**
 * Configura cómo Spring convierte los parámetros de la URL en un {@code Pageable}.
 *
 * <p>{@code @EnableSpringDataWebSupport} se declara aquí para no depender de que la
 * autoconfiguración de Spring Boot 4 lo active. {@code VIA_DTO} hace que, si algún
 * controlador devolviera un {@code Page} por error, el JSON salga en formato estable.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class PaginacionConfig {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer paginacionPorDefecto() {
        return resolver -> {
            resolver.setPageParameterName(Paginacion.PARAMETRO_PAGINA);
            resolver.setSizeParameterName(Paginacion.PARAMETRO_TAMANO);
            resolver.setMaxPageSize(Paginacion.TAMANO_MAXIMO);
            resolver.setFallbackPageable(PageRequest.of(0, Paginacion.TAMANO_POR_DEFECTO));
        };
    }

    @Bean
    SortHandlerMethodArgumentResolverCustomizer ordenPorDefecto() {
        return resolver -> resolver.setSortParameter(Paginacion.PARAMETRO_ORDEN);
    }
}
