package co.edu.ufpso.helpdesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Comprueba que, con la aplicación completa levantada, Spring MVC usa la configuración de
 * {@link PaginacionConfig}. Igual que {@code contextLoads}, necesita el replica set local.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaginacionArranqueTest {

    @Autowired
    private PageableHandlerMethodArgumentResolver resolver;

    @Test
    void laAplicacionAplicaElMaximoYLosNombresEnEspanol() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addParameter("pagina", "1");
        peticion.addParameter("tamano", "500");

        Pageable pageable = resolver.resolveArgument(parametroPageable(), null,
                new ServletWebRequest(peticion), null);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    private static MethodParameter parametroPageable() throws NoSuchMethodException {
        return new MethodParameter(PaginacionArranqueTest.class.getDeclaredMethod("listar", Pageable.class), 0);
    }

    @SuppressWarnings("unused")
    private static void listar(Pageable pageable) {
    }
}
