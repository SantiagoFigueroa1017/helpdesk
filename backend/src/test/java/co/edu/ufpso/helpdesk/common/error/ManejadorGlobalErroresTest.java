package co.edu.ufpso.helpdesk.common.error;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Prueba el manejador sin levantar Spring Boot ni conectarse a MongoDB. Un controlador de
 * prueba lanza cada tipo de error y se revisa el ProblemDetail que sale.
 */
class ManejadorGlobalErroresTest {

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new ManejadorGlobalErrores())
                .build();
    }

    @Test
    void validacionDevuelve400ConLaListaDeErrores() throws Exception {
        mvc.perform(post("/prueba/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"cantidad\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:helpdesk:problema:validacion"))
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/prueba/validacion"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.fecha").exists())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("nombre", "cantidad")));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/prueba/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"));
    }

    @Test
    void parametroFaltanteOConTipoErradoDevuelve400() throws Exception {
        mvc.perform(get("/prueba/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detail").value(containsString("pagina")));

        mvc.perform(get("/prueba/parametro").param("pagina", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void recursoNoEncontradoDevuelve404ConElDetalleDelServicio() throws Exception {
        mvc.perform(get("/prueba/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("No existe el ticket solicitado."));
    }

    @Test
    void rutaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RUTA_NO_ENCONTRADA"));
    }

    @Test
    void metodoNoPermitidoDevuelve405() throws Exception {
        mvc.perform(post("/prueba/no-encontrado"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void borradoBloqueadoDevuelve409ConLosDependientes() throws Exception {
        mvc.perform(get("/prueba/dependientes"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIENE_DEPENDIENTES"))
                .andExpect(jsonPath("$.dependientes.usuarios").value(12));
    }

    @Test
    void reglaDeNegocioDevuelve422() throws Exception {
        mvc.perform(get("/prueba/regla"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    @Test
    void claveDuplicadaDevuelve409SinMostrarElValor() throws Exception {
        mvc.perform(get("/prueba/duplicado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"))
                .andExpect(jsonPath("$.detail").value(not(containsString("900123456"))));
    }

    @Test
    void rechazoDelValidadorDeMongoDevuelve500ConIdError() throws Exception {
        mvc.perform(get("/prueba/esquema"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ESQUEMA_RECHAZADO"))
                .andExpect(jsonPath("$.idError").exists());
    }

    @Test
    void errorInesperadoDevuelve500SinElMensajeInterno() throws Exception {
        mvc.perform(get("/prueba/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.idError").exists())
                .andExpect(jsonPath("$.detail").value(not(containsString("detalle interno"))));
    }

    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {

        record Solicitud(
                @NotBlank(message = "El nombre es obligatorio.") String nombre,
                @Min(value = 1, message = "La cantidad mínima es 1.") int cantidad) {
        }

        @PostMapping("/validacion")
        void validacion(@Valid @RequestBody Solicitud solicitud) {
        }

        @GetMapping("/parametro")
        void parametro(@RequestParam int pagina) {
        }

        @GetMapping("/no-encontrado")
        void noEncontrado() {
            throw new RecursoNoEncontradoException("No existe el ticket solicitado.");
        }

        @GetMapping("/dependientes")
        void dependientes() {
            throw ConflictoException.tieneDependientes("La empresa tiene registros asociados.",
                    Map.of("usuarios", 12L));
        }

        @GetMapping("/regla")
        void regla() {
            throw new ReglaNegocioException("El plan ya alcanzó su máximo de usuarios activos.");
        }

        @GetMapping("/duplicado")
        void duplicado() {
            throw new DuplicateKeyException("E11000 duplicate key error collection: helpdesk.empresas "
                    + "index: ux_empresas_nit dup key: { nit: \"900123456-7\" }");
        }

        @GetMapping("/esquema")
        void esquema() {
            BsonDocument respuesta = new BsonDocument("ok", new BsonDouble(0))
                    .append("code", new BsonInt32(121))
                    .append("errmsg", new BsonString("Document failed validation"));
            throw new DataIntegrityViolationException("Document failed validation",
                    new MongoCommandException(respuesta, new ServerAddress()));
        }

        @GetMapping("/inesperado")
        void inesperado() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }
    }
}
