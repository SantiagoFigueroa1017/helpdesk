package co.edu.ufpso.helpdesk.common.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de errores de la API.
 *
 * <p>Cada código fija el estado HTTP, el título y un detalle por defecto. El frontend decide
 * qué mostrar leyendo la propiedad {@code codigo} del ProblemDetail, nunca el texto.
 * Los 401 y los 403 de Spring Security (filtros) salen por el mismo manejador que el resto
 * (acta 2.4): ver {@code security.ErroresSeguridad}.
 */
public enum CodigoError {

    // 400
    VALIDACION(HttpStatus.BAD_REQUEST, "Datos no válidos",
            "Uno o más campos no cumplen las reglas."),
    SOLICITUD_MAL_FORMADA(HttpStatus.BAD_REQUEST, "Solicitud mal formada",
            "El cuerpo de la solicitud no se pudo leer. Revise que sea JSON válido."),
    PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST, "Parámetro no válido",
            "Falta un parámetro o su valor no tiene el tipo esperado."),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "Solicitud no válida",
            "La solicitud no se pudo procesar."),

    // 401. Ante NO_AUTENTICADO o TOKEN_INVALIDO el frontend renueva el access token con /api/auth/refresh.
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Autenticación requerida",
            "Inicie sesión para usar este recurso."),
    TOKEN_INVALIDO(HttpStatus.UNAUTHORIZED, "Token no válido",
            "El token de acceso no es válido o ya venció."),
    // Estos dos no se resuelven con /refresh: el frontend vuelve a la pantalla de login.
    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "Credenciales no válidas",
            "El correo o la contraseña no son correctos."),
    SESION_INVALIDA(HttpStatus.UNAUTHORIZED, "Sesión no válida",
            "La sesión venció o se cerró. Inicie sesión de nuevo."),

    // 403
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "Acceso denegado",
            "No tiene permiso para realizar esta operación."),
    CUENTA_INACTIVA(HttpStatus.FORBIDDEN, "Cuenta inactiva",
            "El usuario o todos sus roles están inactivos. Comuníquese con el administrador."),
    SOPORTE_BLOQUEADO(HttpStatus.FORBIDDEN, "Membresía sin soporte",
            "La membresía de la empresa no está activa o ya venció."),

    // 404
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso no encontrado",
            "El recurso solicitado no existe."),
    RUTA_NO_ENCONTRADA(HttpStatus.NOT_FOUND, "Ruta no encontrada",
            "No existe un endpoint en esa ruta."),

    // 405 y 415
    METODO_NO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
            "La ruta existe pero no acepta ese método HTTP."),
    TIPO_CONTENIDO_NO_SOPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
            "Envíe el cuerpo como application/json."),

    // 409
    CONFLICTO(HttpStatus.CONFLICT, "Conflicto",
            "La operación choca con el estado actual de los datos."),
    DUPLICADO(HttpStatus.CONFLICT, "Registro duplicado",
            "Ya existe un registro con esos datos."),
    TIENE_DEPENDIENTES(HttpStatus.CONFLICT, "Registro con dependientes",
            "No se puede eliminar porque otros registros dependen de él."),

    // 422. Se usa valueOf porque Spring Framework 7 renombró la constante de 422.
    REGLA_NEGOCIO(HttpStatus.valueOf(422), "Regla de negocio incumplida",
            "La operación no cumple una regla del sistema."),

    // 429. El login lleva además la cabecera Retry-After (acta 2.4).
    DEMASIADOS_INTENTOS(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos",
            "Hubo demasiados intentos fallidos de inicio de sesión. Espere unos minutos e intente de nuevo."),

    // 500 y 503
    ESQUEMA_RECHAZADO(HttpStatus.INTERNAL_SERVER_ERROR, "Documento rechazado por la base de datos",
            "La base de datos rechazó el documento. Es un error del servidor; reporte el idError."),
    BASE_DATOS_NO_DISPONIBLE(HttpStatus.SERVICE_UNAVAILABLE, "Base de datos no disponible",
            "No hay conexión con la base de datos. Intente de nuevo en unos minutos."),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
            "Ocurrió un error inesperado. Reporte el idError.");

    private static final String PREFIJO_TIPO = "urn:helpdesk:problema:";

    private final HttpStatus estado;
    private final String titulo;
    private final String detalle;
    private final URI tipo;

    CodigoError(HttpStatus estado, String titulo, String detalle) {
        this.estado = estado;
        this.titulo = titulo;
        this.detalle = detalle;
        this.tipo = URI.create(PREFIJO_TIPO + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getTitulo() {
        return titulo;
    }

    /** Detalle por defecto, para cuando quien lanza el error no da uno propio. */
    public String getDetalle() {
        return detalle;
    }

    /** Valor del campo {@code type} del ProblemDetail, por ejemplo {@code urn:helpdesk:problema:duplicado}. */
    public URI getTipo() {
        return tipo;
    }
}
