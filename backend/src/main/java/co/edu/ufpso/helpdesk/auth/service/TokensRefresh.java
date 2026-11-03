package co.edu.ufpso.helpdesk.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y hash del refresh token (acta 2.4).
 *
 * <ul>
 *   <li>Token: 32 bytes de {@link SecureRandom} (256 bits) en Base64URL sin relleno, 43 caracteres.
 *       Es lo que viaja en la cookie y nunca se guarda.</li>
 *   <li>Hash: SHA-256 del token en hexadecimal minúscula, 64 caracteres. Es lo que se guarda en
 *       {@code refreshTokens.tokenHash}; el validador exige ese formato.</li>
 * </ul>
 */
public final class TokensRefresh {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES = 32;

    private TokensRefresh() {
    }

    public static String generar() {
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
