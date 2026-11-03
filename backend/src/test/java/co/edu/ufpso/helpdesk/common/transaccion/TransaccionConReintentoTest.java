package co.edu.ufpso.helpdesk.common.transaccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.transaction.PlatformTransactionManager;

import com.mongodb.MongoException;

import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;

/**
 * Reintento de transacciones (tarea 2.8), sin MongoDB: el administrador de transacciones es un simulacro
 * y el choque es un {@link MongoException} con la etiqueta {@code TransientTransactionError}, envuelto
 * como lo entrega Spring Data.
 */
class TransaccionConReintentoTest {

    private final TransaccionConReintento transacciones =
            new TransaccionConReintento(mock(PlatformTransactionManager.class));

    private static RuntimeException choque() {
        MongoException causa = new MongoException(112, "WriteConflict");
        causa.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        return new UncategorizedMongoDbException("Write conflict during plan execution", causa);
    }

    @Test
    void repiteElTrabajoCuandoChocaConOtraTransaccion() {
        AtomicInteger intentos = new AtomicInteger();

        String resultado = transacciones.ejecutar(() -> {
            if (intentos.incrementAndGet() < TransaccionConReintento.MAX_INTENTOS) {
                throw choque();
            }
            return "listo";
        });

        assertThat(resultado).isEqualTo("listo");
        assertThat(intentos).hasValue(TransaccionConReintento.MAX_INTENTOS);
    }

    @Test
    void siSigueChocandoResponde409() {
        AtomicInteger intentos = new AtomicInteger();

        assertThatThrownBy(() -> transacciones.ejecutar(() -> {
            intentos.incrementAndGet();
            throw choque();
        }))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCodigo())
                .isEqualTo(CodigoError.CONFLICTO);
        assertThat(intentos).hasValue(TransaccionConReintento.MAX_INTENTOS);
    }

    @Test
    void unaReglaDeNegocioNoSeRepite() {
        AtomicInteger intentos = new AtomicInteger();

        assertThatThrownBy(() -> transacciones.ejecutar(() -> {
            intentos.incrementAndGet();
            throw new ReglaNegocioException("La capacitación no tiene cupos disponibles.");
        })).isInstanceOf(ReglaNegocioException.class);
        assertThat(intentos).hasValue(1);
    }

    @Test
    void reconoceLaEtiquetaEnLaCadenaDeCausas() {
        assertThat(TransaccionConReintento.esTransitorio(choque())).isTrue();
        assertThat(TransaccionConReintento.esTransitorio(new MongoException(121, "Document failed validation")))
                .isFalse();
        assertThat(TransaccionConReintento.esTransitorio(new IllegalStateException("otro"))).isFalse();
    }
}
