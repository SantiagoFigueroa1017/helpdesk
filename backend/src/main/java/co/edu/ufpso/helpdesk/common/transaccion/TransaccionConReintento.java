package co.edu.ufpso.helpdesk.common.transaccion;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mongodb.MongoException;

import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;

/**
 * Transacción de MongoDB que se repite cuando choca con otra (tarea 2.8).
 *
 * <p>Por qué hace falta: si dos transacciones escriben el mismo documento a la vez (por ejemplo, dos
 * personas que toman el último cupo de una capacitación), MongoDB aborta una con {@code WriteConflict}
 * y le pone la etiqueta {@code TransientTransactionError}. La propia documentación de MongoDB indica
 * repetir la transacción completa. {@code @Transactional} no lo hace, y el error saldría como un 500.
 *
 * <p>Cómo se usa: el servicio pasa el trabajo como una función. Cada intento vuelve a leer los datos,
 * así que la transacción que perdió el choque ve el estado nuevo (por ejemplo, el cupo ya lleno) y
 * responde con la regla de negocio que corresponda. Un {@link ApiException} no se repite: aborta la
 * transacción y sale tal cual.
 *
 * <p>Si después de {@value #MAX_INTENTOS} intentos sigue chocando, responde 409 {@code CONFLICTO}.
 * Nunca se llama a Gemini ni a otro servicio externo dentro del trabajo (contexto 8.2).
 */
@Component
public class TransaccionConReintento {

    public static final int MAX_INTENTOS = 3;

    private static final Logger log = LoggerFactory.getLogger(TransaccionConReintento.class);

    private final TransactionTemplate plantilla;

    public TransaccionConReintento(PlatformTransactionManager transactionManager) {
        this.plantilla = new TransactionTemplate(transactionManager);
    }

    public <T> T ejecutar(Supplier<T> trabajo) {
        for (int intento = 1;; intento++) {
            try {
                return plantilla.execute(estado -> trabajo.get());
            } catch (RuntimeException e) {
                if (!esTransitorio(e)) {
                    throw e;
                }
                if (intento >= MAX_INTENTOS) {
                    log.warn("La transacción chocó {} veces con otras operaciones; se responde 409", intento);
                    throw new ApiException(CodigoError.CONFLICTO,
                            "Hubo varias operaciones sobre los mismos datos al mismo tiempo. Intente de nuevo.");
                }
                log.debug("Conflicto transitorio en el intento {}; se repite la transacción", intento);
                esperar(intento);
            }
        }
    }

    /**
     * Espera corta y aleatoria antes de repetir (de 5 a 25 ms en el primer reintento, el doble en el
     * segundo), para que las transacciones que chocaron no vuelvan a empezar en el mismo instante.
     */
    private static void esperar(int intento) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 26) * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(CodigoError.CONFLICTO, "La operación se interrumpió. Intente de nuevo.");
        }
    }

    /** Busca en la cadena de causas un error de MongoDB con la etiqueta {@code TransientTransactionError}. */
    static boolean esTransitorio(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
