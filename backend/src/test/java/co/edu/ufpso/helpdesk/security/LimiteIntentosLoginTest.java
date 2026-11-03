package co.edu.ufpso.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import co.edu.ufpso.helpdesk.common.error.DemasiadosIntentosException;

/** Reglas del límite de intentos (acta 2.4) con un reloj que la prueba adelanta. Sin Spring. */
class LimiteIntentosLoginTest {

    /** Reloj que solo avanza cuando la prueba lo pide. */
    static final class RelojAjustable extends Clock {
        private Instant ahora = Instant.parse("2026-11-03T05:00:00Z");

        void adelantar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    private static final String CORREO = "natalia.ascanio@ferreteria.co";
    private static final String IP = "10.0.0.7";

    private final RelojAjustable reloj = new RelojAjustable();
    private final LimiteIntentosLogin limite =
            new LimiteIntentosLogin(new PropiedadesLimiteLogin(5, 20, Duration.ofMinutes(15)), reloj);

    private void fallar(int veces, String correo, String ip) {
        for (int i = 0; i < veces; i++) {
            limite.registrarFallo(correo, ip);
        }
    }

    @Test
    void cuatroFallosNoBloqueanYElQuintoSi() {
        fallar(4, CORREO, IP);
        assertThatCode(() -> limite.verificar(CORREO, IP)).doesNotThrowAnyException();

        fallar(1, CORREO, IP);
        assertThatThrownBy(() -> limite.verificar(CORREO, IP))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void elBloqueoDuraQuinceMinutosDesdeElPrimerFallo() {
        fallar(1, CORREO, IP);
        reloj.adelantar(Duration.ofMinutes(10));
        fallar(4, CORREO, IP);

        DemasiadosIntentosException ex = (DemasiadosIntentosException) org.assertj.core.api.Assertions
                .catchThrowable(() -> limite.verificar(CORREO, IP));
        assertThat(ex.getSegundos()).isEqualTo(5 * 60);

        reloj.adelantar(Duration.ofMinutes(5));
        assertThatCode(() -> limite.verificar(CORREO, IP)).doesNotThrowAnyException();
    }

    @Test
    void elCorreoSeComparaSinMayusculasNiEspacios() {
        fallar(5, "  Natalia.Ascanio@Ferreteria.CO ", IP);
        assertThatThrownBy(() -> limite.verificar(CORREO, "10.0.0.8"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void unLoginCorrectoBorraElContadorDelCorreo() {
        fallar(4, CORREO, IP);
        limite.registrarExito(CORREO);
        fallar(4, CORREO, IP);
        assertThatCode(() -> limite.verificar(CORREO, IP)).doesNotThrowAnyException();
    }

    @Test
    void veinteFallosDesdeUnaIpConCorreosDistintosBloqueanEsaIp() {
        for (int i = 0; i < 20; i++) {
            limite.registrarFallo("usuario" + i + "@ferreteria.co", IP);
        }
        assertThatThrownBy(() -> limite.verificar("otro@ferreteria.co", IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> limite.verificar("otro@ferreteria.co", "10.0.0.8")).doesNotThrowAnyException();
    }
}
