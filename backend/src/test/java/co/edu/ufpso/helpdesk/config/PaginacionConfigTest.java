package co.edu.ufpso.helpdesk.config;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.common.error.ManejadorGlobalErrores;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.common.paginacion.Paginacion;

/**
 * Prueba la paginación sin levantar Spring Boot. Los resolvers se configuran con los mismos
 * beans de {@link PaginacionConfig} que usa la aplicación.
 */
class PaginacionConfigTest {

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        PaginacionConfig config = new PaginacionConfig();
        SortHandlerMethodArgumentResolver orden = new SortHandlerMethodArgumentResolver();
        config.ordenPorDefecto().customize(orden);
        PageableHandlerMethodArgumentResolver paginacion = new PageableHandlerMethodArgumentResolver(orden);
        config.paginacionPorDefecto().customize(paginacion);

        mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setCustomArgumentResolvers(paginacion)
                .setControllerAdvice(new ManejadorGlobalErrores())
                .build();
    }

    @Test
    void sinParametrosUsaPaginaCeroY20Elementos() throws Exception {
        mvc.perform(get("/prueba/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(250))
                .andExpect(jsonPath("$.totalPaginas").value(13))
                .andExpect(jsonPath("$.contenido", hasSize(20)));
    }

    @Test
    void tamanoMayorAlMaximoSeRecortaA100() throws Exception {
        mvc.perform(get("/prueba/items").param("tamano", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(100))
                .andExpect(jsonPath("$.contenido", hasSize(100)));
    }

    @Test
    void paginaYTamanoSeRespetan() throws Exception {
        mvc.perform(get("/prueba/items").param("pagina", "2").param("tamano", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.contenido[0]").value("item-100"));
    }

    @Test
    void losNombresEnInglesSeIgnoran() throws Exception {
        mvc.perform(get("/prueba/items").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20));
    }

    @Test
    void ordenPorCampoPermitidoFunciona() throws Exception {
        mvc.perform(get("/prueba/items").param("orden", "fechaCreacion,desc"))
                .andExpect(status().isOk());
    }

    @Test
    void ordenPorCampoNoPermitidoDevuelve400() throws Exception {
        mvc.perform(get("/prueba/items").param("orden", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @RestController
    static class ControladorDePrueba {

        private static final List<String> DATOS =
                IntStream.range(0, 250).mapToObj(i -> "item-" + i).toList();

        @GetMapping("/prueba/items")
        Pagina<String> listar(Pageable pageable) {
            Paginacion.validarOrden(pageable, Set.of("fechaCreacion", "prioridad"));
            int desde = (int) Math.min(pageable.getOffset(), DATOS.size());
            int hasta = Math.min(desde + pageable.getPageSize(), DATOS.size());
            return Pagina.de(new PageImpl<>(DATOS.subList(desde, hasta), pageable, DATOS.size()));
        }
    }
}
