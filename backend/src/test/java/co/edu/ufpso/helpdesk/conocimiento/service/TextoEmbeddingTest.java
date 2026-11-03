package co.edu.ufpso.helpdesk.conocimiento.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Texto que se envía a Gemini (modelo 1.2): título, etiquetas y contenido, hasta 7.000 caracteres. */
class TextoEmbeddingTest {

    @Test
    void uneTituloEtiquetasYContenido() {
        assertThat(TextoEmbedding.de("Caja lenta", List.of("caja", "pos"), "Reinicie el turno."))
                .isEqualTo("Caja lenta\ncaja, pos\nReinicie el turno.");
    }

    @Test
    void recortaASieteMilCaracteres() {
        String texto = TextoEmbedding.de("T", List.of(), "a".repeat(10_000));
        assertThat(texto).hasSize(TextoEmbedding.MAXIMO);
    }

    @Test
    void noPartePorLaMitadUnCaracterDeDosUnidades() {
        // "T\n\n" ocupa 3 posiciones: el emoji queda en las posiciones 6999 y 7000.
        String contenido = "a".repeat(TextoEmbedding.MAXIMO - 4) + "😀" + "fin";
        String texto = TextoEmbedding.de("T", List.of(), contenido);
        assertThat(texto).hasSize(TextoEmbedding.MAXIMO - 1);
        assertThat(Character.isHighSurrogate(texto.charAt(texto.length() - 1))).isFalse();
    }
}
