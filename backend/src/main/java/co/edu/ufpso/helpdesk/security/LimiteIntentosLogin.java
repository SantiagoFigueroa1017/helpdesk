package co.edu.ufpso.helpdesk.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import co.edu.ufpso.helpdesk.common.error.DemasiadosIntentosException;

/**
 * Límite de intentos fallidos en el login, contra fuerza bruta (contexto 8.3, acta 2.4).
 *
 * <p>Dos contadores en memoria con Caffeine, uno por correo y otro por IP. Cada contador vive desde
 * su primer fallo hasta que pasa la ventana (15 minutos); los fallos siguientes no la alargan.
 * Mientras un contador está en su máximo, el login responde 429 {@code DEMASIADOS_INTENTOS} sin
 * comprobar la contraseña. Un login correcto borra el contador del correo, no el de la IP.
 *
 * <p>Vive en memoria porque el backend es un solo proceso. Si se reinicia, los contadores empiezan
 * de cero. La IP es la del socket ({@code getRemoteAddr}); detrás de un proxy habría que leerla de
 * {@code X-Forwarded-For}, cosa que hoy no aplica.
 */
@Component
public class LimiteIntentosLogin {

    private static final Logger log = LoggerFactory.getLogger(LimiteIntentosLogin.class);

    /** Fallos acumulados y el instante del primero, que fija cuándo se borra el contador. */
    record Fallos(int cantidad, Instant primero) {
    }

    private final PropiedadesLimiteLogin propiedades;
    private final Clock reloj;
    private final Cache<String, Fallos> porCorreo;
    private final Cache<String, Fallos> porIp;

    public LimiteIntentosLogin(PropiedadesLimiteLogin propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.porCorreo = nuevoContador();
        this.porIp = nuevoContador();
    }

    /** Lanza {@link DemasiadosIntentosException} si el correo o la IP están bloqueados. */
    public void verificar(String email, String ip) {
        Optional<Duration> espera = bloqueo(porIp, ip, propiedades.maxFallosIp())
                .or(() -> bloqueo(porCorreo, clave(email), propiedades.maxFallosCorreo()));
        if (espera.isPresent()) {
            long segundos = Math.max(1, (espera.get().toMillis() + 999) / 1000);
            throw new DemasiadosIntentosException(segundos);
        }
    }

    public void registrarFallo(String email, String ip) {
        Fallos correo = sumar(porCorreo, clave(email));
        sumar(porIp, ip);
        if (correo.cantidad() == propiedades.maxFallosCorreo()) {
            // Sin el correo en el log: es un dato personal y quien lo escribe puede no ser su dueño.
            log.warn("Login bloqueado por {} fallos con un mismo correo desde {}", correo.cantidad(), ip);
        }
    }

    public void registrarExito(String email) {
        porCorreo.invalidate(clave(email));
    }

    /** Borra todos los contadores. Lo usan las pruebas para empezar cada caso en cero. */
    public void limpiar() {
        porCorreo.invalidateAll();
        porIp.invalidateAll();
    }

    private Optional<Duration> bloqueo(Cache<String, Fallos> contador, String clave, int maximo) {
        if (clave == null) {
            return Optional.empty();
        }
        Fallos fallos = contador.getIfPresent(clave);
        if (fallos == null || fallos.cantidad() < maximo) {
            return Optional.empty();
        }
        Duration restante = Duration.between(reloj.instant(), fallos.primero().plus(propiedades.ventana()));
        return restante.isNegative() || restante.isZero() ? Optional.empty() : Optional.of(restante);
    }

    private Fallos sumar(Cache<String, Fallos> contador, String clave) {
        if (clave == null) {
            return new Fallos(0, reloj.instant());
        }
        return contador.asMap().compute(clave, (k, actual) -> actual == null
                ? new Fallos(1, reloj.instant())
                : new Fallos(actual.cantidad() + 1, actual.primero()));
    }

    private static String clave(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * El contador expira a la ventana de su creación; actualizarlo no reinicia el plazo. El reloj de
     * Caffeine sale del {@link Clock} del sistema para que las pruebas puedan adelantar la hora.
     */
    private Cache<String, Fallos> nuevoContador() {
        long ventana = propiedades.ventana().toNanos();
        return Caffeine.newBuilder()
                .maximumSize(100_000)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(reloj.millis()))
                .expireAfter(new Expiry<String, Fallos>() {
                    @Override
                    public long expireAfterCreate(String clave, Fallos valor, long ahora) {
                        return ventana;
                    }

                    @Override
                    public long expireAfterUpdate(String clave, Fallos valor, long ahora, long restante) {
                        return restante;
                    }

                    @Override
                    public long expireAfterRead(String clave, Fallos valor, long ahora, long restante) {
                        return restante;
                    }
                })
                .build();
    }
}
