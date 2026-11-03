package co.edu.ufpso.helpdesk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Formato del refresh token y de su hash (acta 2.4). */
class TokensRefreshTest {

    @Test
    void elTokenSon43CaracteresBase64UrlSinRelleno() {
        String token = TokensRefresh.generar();
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    void cadaTokenEsDistinto() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(TokensRefresh.generar());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void elHashEsSha256EnHexadecimalMinusculaComoExigeElValidador() {
        // Vector de prueba de SHA-256 para "abc" (FIPS 180-2).
        assertThat(TokensRefresh.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokensRefresh.hash(TokensRefresh.generar())).matches("^[0-9a-f]{64}$");
    }
}
