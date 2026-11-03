package co.edu.ufpso.helpdesk.conocimiento.service;

import java.util.List;

/**
 * Texto que se convierte en vector (modelo 1.2, "Embeddings"): título, etiquetas y contenido, recortado a
 * {@value #MAXIMO} caracteres para no pasar del límite de 2.048 tokens de Gemini. El vector representa el
 * comienzo del artículo; el índice de texto cubre el contenido completo.
 */
public final class TextoEmbedding {

    public static final int MAXIMO = 7000;

    private TextoEmbedding() {
    }

    public static String de(String titulo, List<String> etiquetas, String contenido) {
        String texto = titulo + "\n" + String.join(", ", etiquetas) + "\n" + contenido;
        if (texto.length() <= MAXIMO) {
            return texto;
        }
        // No se corta un carácter de dos unidades (emojis y similares) por la mitad.
        int fin = Character.isHighSurrogate(texto.charAt(MAXIMO - 1)) ? MAXIMO - 1 : MAXIMO;
        return texto.substring(0, fin);
    }
}
