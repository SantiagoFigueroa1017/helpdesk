package co.edu.ufpso.helpdesk.common.error;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoServerException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Punto único donde cualquier excepción se convierte en una respuesta ProblemDetail (RFC 9457).
 *
 * <p>Todas las respuestas de error llevan los campos del RFC ({@code type}, {@code title},
 * {@code status}, {@code detail}, {@code instance}) y además:
 * <ul>
 *   <li>{@code codigo}: el nombre de {@link CodigoError}. Es lo que lee el frontend.</li>
 *   <li>{@code fecha}: instante del error en UTC, formato ISO-8601.</li>
 *   <li>{@code errores}: solo en validación, lista de {@link ErrorCampo}.</li>
 *   <li>{@code idError}: solo en errores 5xx. El mismo id queda en el log con la traza completa.</li>
 * </ul>
 *
 * <p>Hereda de {@link ResponseEntityExceptionHandler} para que las excepciones propias de
 * Spring MVC (JSON mal formado, método no permitido, ruta inexistente...) salgan con el mismo
 * formato. Todas pasan por {@link #handleExceptionInternal}.
 *
 * <p>Los errores 4xx se registran en DEBUG porque son fallos del cliente. Los 5xx en ERROR,
 * con traza. Nunca se registra el cuerpo de la petición.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Código de MongoDB cuando un documento no pasa el validador $jsonSchema. */
    private static final int MONGO_DOCUMENTO_NO_VALIDO = 121;

    /** Saca el nombre del índice del mensaje E11000, por ejemplo {@code ux_empresas_nit}. */
    private static final Pattern INDICE_DUPLICADO = Pattern.compile("index: (\\S+)");

    // ------------------------------------------------------------------
    // Excepciones del proyecto
    // ------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> manejarApiException(ApiException ex, HttpServletRequest request) {
        log.debug("{} en {}: {}", ex.getCodigo(), request.getRequestURI(), ex.getMessage());
        ProblemDetail problema = crear(ex.getCodigo(), ex.getMessage(), request.getRequestURI());
        ex.getPropiedades().forEach(problema::setProperty);
        return responder(problema);
    }

    /** Validación con {@code @Validated} fuera de los controladores, por ejemplo en un servicio. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> manejarConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorCampo(ultimoNodo(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        ProblemDetail problema = crear(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                request.getRequestURI());
        problema.setProperty("errores", errores);
        return responder(problema);
    }

    // ------------------------------------------------------------------
    // Spring Security
    // ------------------------------------------------------------------

    /**
     * Petición sin token o con un token que no se pudo validar. Llega desde los filtros a través
     * de {@code security.ErroresSeguridad}. Un token mal firmado, mal formado o vencido es una
     * {@link OAuth2AuthenticationException} y responde {@code TOKEN_INVALIDO}; la falta de token
     * responde {@code NO_AUTENTICADO}. El mensaje de la excepción no sale al cliente ni lleva
     * el token.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> manejarNoAutenticado(
            AuthenticationException ex, HttpServletRequest request) {
        CodigoError codigo = (ex instanceof OAuth2AuthenticationException)
                ? CodigoError.TOKEN_INVALIDO
                : CodigoError.NO_AUTENTICADO;
        log.debug("{} en {}: {}", codigo, request.getRequestURI(), ex.getClass().getSimpleName());
        return responder(crear(codigo, codigo.getDetalle(), request.getRequestURI()));
    }

    /**
     * Usuario autenticado sin el permiso. Cubre los dos caminos: el de los filtros (vía
     * {@code security.ErroresSeguridad}) y el de {@code @PreAuthorize}, cuya
     * {@code AuthorizationDeniedException} sale del controlador y sin este manejador caería
     * en {@link #manejarInesperada} como 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> manejarAccesoDenegado(
            AccessDeniedException ex, HttpServletRequest request) {
        log.debug("ACCESO_DENEGADO en {}", request.getRequestURI());
        return responder(crear(CodigoError.ACCESO_DENEGADO, CodigoError.ACCESO_DENEGADO.getDetalle(),
                request.getRequestURI()));
    }

    // ------------------------------------------------------------------
    // MongoDB (Spring Data traduce las excepciones del driver a org.springframework.dao)
    // ------------------------------------------------------------------

    /**
     * Un índice único rechazó la escritura. Los servicios comprueban antes de insertar y
     * lanzan {@link ConflictoException#duplicado}; esto cubre el caso de dos peticiones
     * simultáneas. El valor duplicado no sale en la respuesta ni en el log.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> manejarDuplicado(DuplicateKeyException ex, HttpServletRequest request) {
        log.warn("Clave duplicada en {} (índice {})", request.getRequestURI(), indiceDuplicado(ex));
        return responder(crear(CodigoError.DUPLICADO, CodigoError.DUPLICADO.getDetalle(),
                request.getRequestURI()));
    }

    /**
     * Si el validador $jsonSchema rechazó el documento, el DTO dejó pasar un dato que el
     * esquema no acepta: es un error del backend y responde 500. El mensaje del driver trae
     * la regla que falló y queda en el log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarIntegridad(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        if (esRechazoDelEsquema(ex)) {
            return errorDelServidor(CodigoError.ESQUEMA_RECHAZADO, ex, request.getRequestURI());
        }
        log.warn("Violación de integridad en {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return responder(crear(CodigoError.CONFLICTO, CodigoError.CONFLICTO.getDetalle(),
                request.getRequestURI()));
    }

    /** Atlas o el replica set local no responden (red, timeout, servidor caído). */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ProblemDetail> manejarBaseNoDisponible(
            DataAccessResourceFailureException ex, HttpServletRequest request) {
        return errorDelServidor(CodigoError.BASE_DATOS_NO_DISPONIBLE, ex, request.getRequestURI());
    }

    /** Todo lo que no tiene un manejador más específico. El mensaje interno nunca sale al cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarInesperada(Exception ex, HttpServletRequest request) {
        return errorDelServidor(CodigoError.ERROR_INTERNO, ex, request.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Excepciones de Spring MVC
    // ------------------------------------------------------------------

    /**
     * Spring ya armó un ProblemDetail en inglés para su excepción. Aquí se le ponen el tipo,
     * el título y el detalle en español, el {@code codigo} y la {@code fecha}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta == null || !(respuesta.getBody() instanceof ProblemDetail problema)) {
            return respuesta; // la respuesta ya se había enviado; no hay nada que completar
        }

        String ruta = rutaDe(request);
        Descripcion descripcion = describir(ex, statusCode);
        completar(problema, descripcion.codigo(), descripcion.detalle(), ruta);
        if (!descripcion.errores().isEmpty()) {
            problema.setProperty("errores", descripcion.errores());
        }

        if (statusCode.is5xxServerError()) {
            String idError = nuevoIdError();
            problema.setProperty("idError", idError);
            log.error("[{}] {} en {}", idError, descripcion.codigo(), ruta, ex);
        } else {
            log.debug("{} en {}: {}", descripcion.codigo(), ruta, ex.getMessage());
        }
        return respuesta;
    }

    private record Descripcion(CodigoError codigo, String detalle, List<ErrorCampo> errores) {
        Descripcion(CodigoError codigo, String detalle) {
            this(codigo, detalle, List.of());
        }

        Descripcion(CodigoError codigo) {
            this(codigo, codigo.getDetalle(), List.of());
        }
    }

    private Descripcion describir(Exception ex, HttpStatusCode estado) {
        if (estado.is5xxServerError()) {
            // Spring responde 5xx a fallas de configuración del servidor; el detalle no se expone.
            return new Descripcion(CodigoError.ERROR_INTERNO);
        }
        return switch (ex) {
            case MethodArgumentNotValidException e ->
                    new Descripcion(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                            erroresDe(e.getBindingResult()));
            case HandlerMethodValidationException e ->
                    new Descripcion(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(), erroresDe(e));
            case HttpMessageNotReadableException e ->
                    new Descripcion(CodigoError.SOLICITUD_MAL_FORMADA);
            case MissingServletRequestParameterException e ->
                    new Descripcion(CodigoError.PARAMETRO_INVALIDO,
                            "Falta el parámetro obligatorio '" + e.getParameterName() + "'.");
            case MissingRequestValueException e ->
                    new Descripcion(CodigoError.PARAMETRO_INVALIDO);
            case MethodArgumentTypeMismatchException e ->
                    new Descripcion(CodigoError.PARAMETRO_INVALIDO,
                            "El valor del parámetro '" + e.getName() + "' no tiene el tipo esperado.");
            case TypeMismatchException e ->
                    new Descripcion(CodigoError.PARAMETRO_INVALIDO);
            case NoResourceFoundException e ->
                    new Descripcion(CodigoError.RUTA_NO_ENCONTRADA);
            case NoHandlerFoundException e ->
                    new Descripcion(CodigoError.RUTA_NO_ENCONTRADA);
            case HttpRequestMethodNotSupportedException e ->
                    new Descripcion(CodigoError.METODO_NO_PERMITIDO,
                            "La ruta no acepta el método " + e.getMethod() + ".");
            case HttpMediaTypeNotSupportedException e ->
                    new Descripcion(CodigoError.TIPO_CONTENIDO_NO_SOPORTADO);
            default ->
                    new Descripcion(CodigoError.SOLICITUD_INVALIDA);
        };
    }

    // ------------------------------------------------------------------
    // Construcción de la respuesta
    // ------------------------------------------------------------------

    private static ProblemDetail crear(CodigoError codigo, String detalle, String ruta) {
        ProblemDetail problema = ProblemDetail.forStatus(codigo.getEstado());
        completar(problema, codigo, detalle, ruta);
        return problema;
    }

    /** No cambia el {@code status}: en errores de Spring se conserva el que Spring decidió. */
    private static void completar(ProblemDetail problema, CodigoError codigo, String detalle, String ruta) {
        problema.setType(codigo.getTipo());
        problema.setTitle(codigo.getTitulo());
        problema.setDetail(detalle);
        if (ruta != null) {
            problema.setInstance(URI.create(ruta));
        }
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("fecha", Instant.now().toString());
    }

    private static ResponseEntity<ProblemDetail> responder(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    private static ResponseEntity<ProblemDetail> errorDelServidor(CodigoError codigo, Exception ex, String ruta) {
        String idError = nuevoIdError();
        log.error("[{}] {} en {}", idError, codigo, ruta, ex);
        ProblemDetail problema = crear(codigo, codigo.getDetalle(), ruta);
        problema.setProperty("idError", idError);
        return responder(problema);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static List<ErrorCampo> erroresDe(BindingResult resultado) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (FieldError error : resultado.getFieldErrors()) {
            errores.add(new ErrorCampo(error.getField(), error.getDefaultMessage()));
        }
        for (ObjectError error : resultado.getGlobalErrors()) {
            errores.add(new ErrorCampo(error.getObjectName(), error.getDefaultMessage()));
        }
        return errores;
    }

    /** Errores de restricciones puestas en los parámetros del controlador, como {@code @RequestParam @Min(1)}. */
    private static List<ErrorCampo> erroresDe(HandlerMethodValidationException ex) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            String parametro = resultado.getMethodParameter().getParameterName();
            if (parametro == null) {
                parametro = "arg" + resultado.getMethodParameter().getParameterIndex();
            }
            for (MessageSourceResolvable error : resultado.getResolvableErrors()) {
                String campo = (error instanceof FieldError fe) ? fe.getField() : parametro;
                errores.add(new ErrorCampo(campo, error.getDefaultMessage()));
            }
        }
        return errores;
    }

    private static boolean esRechazoDelEsquema(Throwable ex) {
        int profundidad = 0;
        for (Throwable t = ex; t != null && profundidad < 10; t = t.getCause(), profundidad++) {
            if (t instanceof MongoBulkWriteException bulk) {
                return bulk.getWriteErrors().stream().anyMatch(e -> e.getCode() == MONGO_DOCUMENTO_NO_VALIDO);
            }
            if (t instanceof MongoServerException servidor && servidor.getCode() == MONGO_DOCUMENTO_NO_VALIDO) {
                return true;
            }
        }
        return false;
    }

    private static String indiceDuplicado(DuplicateKeyException ex) {
        String mensaje = ex.getMessage();
        if (mensaje == null) {
            return "desconocido";
        }
        Matcher m = INDICE_DUPLICADO.matcher(mensaje);
        return m.find() ? m.group(1) : "desconocido";
    }

    private static String ultimoNodo(String ruta) {
        int punto = ruta.lastIndexOf('.');
        return punto >= 0 ? ruta.substring(punto + 1) : ruta;
    }

    private static String rutaDe(WebRequest request) {
        return (request instanceof ServletWebRequest swr) ? swr.getRequest().getRequestURI() : null;
    }

    private static String nuevoIdError() {
        return UUID.randomUUID().toString();
    }
}
