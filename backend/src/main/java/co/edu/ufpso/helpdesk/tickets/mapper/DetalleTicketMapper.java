package co.edu.ufpso.helpdesk.tickets.mapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ErrorCampo;
import co.edu.ufpso.helpdesk.tickets.domain.CampoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle;
import co.edu.ufpso.helpdesk.tickets.domain.CatalogoDetalle.DefinicionModulo;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoCampo;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;

/**
 * Convierte el {@code detalle} del JSON en el record de su módulo y al revés (tarea 2.6).
 *
 * <p>Entrada: el {@code detalle} llega como objeto JSON libre ({@code Map}), porque su forma
 * depende del {@code modulo} que viene en el mismo cuerpo. Se revisa contra {@link CatalogoDetalle}
 * con las mismas reglas del validador {@code $jsonSchema}: campos obligatorios, campos que no son
 * del módulo, longitudes, opciones y tipos de número. Todos los errores salen juntos en un 400
 * {@code VALIDACION}, con el campo como {@code detalle.<nombre>}, igual que los de Bean Validation.
 * Así el frontend los pinta en el formulario y MongoDB nunca recibe un documento que rechazaría.
 *
 * <p>Un opcional vacío (null o texto en blanco) no se guarda: los formularios envían cadenas vacías.
 */
public final class DetalleTicketMapper {

    /** Tope del dinero, igual que precio y monto en 2.5: 12 enteros y 2 decimales. */
    private static final int ENTEROS_DINERO = 12;
    private static final int DECIMALES_DINERO = 2;

    private DetalleTicketMapper() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    /**
     * Valida el detalle recibido y arma el record del módulo.
     *
     * @return el detalle de un INCIDENTE, o null para una SUGERENCIA
     * @throws ApiException 400 {@code VALIDACION} con la lista {@code errores}
     */
    public static DetalleTicket desdeSolicitud(ModuloErp modulo, TipoTicket tipo, Map<String, Object> entrada) {
        boolean vacio = entrada == null || entrada.isEmpty();
        if (tipo == TipoTicket.SUGERENCIA) {
            if (!vacio) {
                throw errores(List.of(new ErrorCampo("detalle",
                        "Las sugerencias no llevan campos específicos del módulo.")));
            }
            return null;
        }

        DefinicionModulo definicion = CatalogoDetalle.de(modulo);
        Map<String, Object> datos = vacio ? Map.of() : entrada;
        List<ErrorCampo> errores = new ArrayList<>();

        for (String nombre : datos.keySet()) {
            if (definicion.campo(nombre) == null) {
                errores.add(new ErrorCampo("detalle." + nombre,
                        "Este campo no corresponde al módulo " + definicion.etiqueta() + "."));
            }
        }
        Map<String, Object> valores = new HashMap<>();
        for (CampoDetalle campo : definicion.campos()) {
            Object valor = convertir(campo, datos.get(campo.nombre()), errores);
            if (valor != null) {
                valores.put(campo.nombre(), valor);
            }
        }
        if (!errores.isEmpty()) {
            throw errores(errores);
        }
        return construir(definicion.clase(), valores);
    }

    /** Revisa un valor y lo deja en el tipo de Java del record. Null si no hay valor o hay error. */
    private static Object convertir(CampoDetalle campo, Object valor, List<ErrorCampo> errores) {
        String nombre = "detalle." + campo.nombre();
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            if (campo.obligatorio()) {
                errores.add(new ErrorCampo(nombre, campo.etiqueta() + " es obligatorio."));
            }
            return null;
        }
        return switch (campo.tipo()) {
            case TEXTO, TEXTO_LARGO -> texto(campo, valor, nombre, errores);
            case OPCION -> opcion(campo, valor, nombre, errores);
            case ENTERO -> entero(valor, nombre, errores);
            case DECIMAL -> decimal(valor, nombre, errores);
            case DINERO -> dinero(valor, nombre, errores);
        };
    }

    private static String texto(CampoDetalle campo, Object valor, String nombre, List<ErrorCampo> errores) {
        if (!(valor instanceof String s)) {
            errores.add(new ErrorCampo(nombre, "Debe ser texto."));
            return null;
        }
        // Un log se guarda tal cual; un código o una URL, sin espacios sobrantes.
        String limpio = campo.tipo() == TipoCampo.TEXTO ? s.strip() : s;
        if (limpio.length() > campo.longitudMaxima()) {
            errores.add(new ErrorCampo(nombre, "Debe tener como máximo " + campo.longitudMaxima() + " caracteres."));
            return null;
        }
        return limpio;
    }

    private static String opcion(CampoDetalle campo, Object valor, String nombre, List<ErrorCampo> errores) {
        boolean permitido = valor instanceof String s
                && campo.opciones().stream().anyMatch(o -> o.valor().equals(s));
        if (!permitido) {
            List<String> validos = campo.opciones().stream().map(CampoDetalle.Opcion::valor).toList();
            errores.add(new ErrorCampo(nombre, "Valor no permitido. Use uno de: " + String.join(", ", validos) + "."));
            return null;
        }
        return (String) valor;
    }

    private static Integer entero(Object valor, String nombre, List<ErrorCampo> errores) {
        try {
            // 5 y 5.0 son el mismo entero; 5.5 o un número fuera de int32 no lo son.
            return numero(valor).intValueExact();
        } catch (ArithmeticException | NumberFormatException | ClassCastException e) {
            errores.add(new ErrorCampo(nombre, "Debe ser un número entero."));
            return null;
        }
    }

    private static Double decimal(Object valor, String nombre, List<ErrorCampo> errores) {
        if (!(valor instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            errores.add(new ErrorCampo(nombre, "Debe ser un número."));
            return null;
        }
        return n.doubleValue();
    }

    private static BigDecimal dinero(Object valor, String nombre, List<ErrorCampo> errores) {
        BigDecimal monto;
        try {
            monto = numero(valor).stripTrailingZeros();
        } catch (NumberFormatException | ClassCastException e) {
            errores.add(new ErrorCampo(nombre, "Debe ser un número."));
            return null;
        }
        if (monto.scale() < 0) {
            monto = monto.setScale(0);
        }
        if (monto.signum() < 0) {
            errores.add(new ErrorCampo(nombre, "No puede ser negativo."));
        } else if (monto.scale() > DECIMALES_DINERO || monto.precision() - monto.scale() > ENTEROS_DINERO) {
            errores.add(new ErrorCampo(nombre, "Admite hasta " + ENTEROS_DINERO + " enteros y "
                    + DECIMALES_DINERO + " decimales."));
        } else {
            return monto;
        }
        return null;
    }

    /**
     * El número tal como llegó en el JSON. Jackson entrega Integer, Long, BigInteger o Double; el
     * texto de cualquiera de ellos se lee sin perder dígitos con {@code new BigDecimal}.
     */
    private static BigDecimal numero(Object valor) {
        if (!(valor instanceof Number)) {
            throw new ClassCastException();
        }
        return new BigDecimal(valor.toString());
    }

    /**
     * Arma el record con su constructor canónico. Los campos OPCION llegan como texto ya validado y
     * aquí pasan a la constante del enum del componente.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static DetalleTicket construir(Class<? extends DetalleTicket> clase, Map<String, Object> valores) {
        RecordComponent[] componentes = clase.getRecordComponents();
        Class<?>[] tipos = new Class<?>[componentes.length];
        Object[] argumentos = new Object[componentes.length];
        for (int i = 0; i < componentes.length; i++) {
            tipos[i] = componentes[i].getType();
            Object valor = valores.get(componentes[i].getName());
            if (valor != null && tipos[i].isEnum()) {
                valor = Enum.valueOf((Class<? extends Enum>) tipos[i], (String) valor);
            }
            argumentos[i] = valor;
        }
        try {
            Constructor<? extends DetalleTicket> constructor = clase.getDeclaredConstructor(tipos);
            return constructor.newInstance(argumentos);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            throw new IllegalStateException("No se pudo armar " + clase.getSimpleName(), e);
        }
    }

    private static ApiException errores(List<ErrorCampo> errores) {
        return new ApiException(CodigoError.VALIDACION, CodigoError.VALIDACION.getDetalle(),
                Map.of("errores", List.copyOf(errores)));
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    /**
     * El detalle como mapa para la respuesta: los campos con valor, en el orden del catálogo, y los
     * enums por su nombre. Null para una sugerencia.
     */
    public static Map<String, Object> aRespuesta(DetalleTicket detalle) {
        if (detalle == null) {
            return null;
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (RecordComponent componente : detalle.getClass().getRecordComponents()) {
            Object valor;
            try {
                valor = componente.getAccessor().invoke(detalle);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("No se pudo leer " + componente.getName(), e);
            }
            if (valor instanceof Enum<?> constante) {
                valor = constante.name();
            }
            if (valor != null) {
                mapa.put(componente.getName(), valor);
            }
        }
        return mapa;
    }
}
