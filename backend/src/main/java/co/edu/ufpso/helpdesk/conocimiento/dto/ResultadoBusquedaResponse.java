package co.edu.ufpso.helpdesk.conocimiento.dto;

import java.util.List;

/**
 * Respuesta de las dos búsquedas de HU30. Los resultados vienen ordenados por relevancia, el más relevante
 * primero.
 *
 * @param origen     {@code TEXTO} (índice de texto en español) o {@code SEMANTICA} (Atlas Vector Search)
 * @param aviso      null si la búsqueda se hizo como se pidió. Si la búsqueda por significado no se pudo
 *                   hacer, la API responde con la de texto y explica por qué: {@code GEMINI_NO_CONFIGURADO},
 *                   {@code GEMINI_LIMITE_ALCANZADO}, {@code GEMINI_NO_DISPONIBLE} o
 *                   {@code VECTORIAL_NO_DISPONIBLE} (perfil local, sin Atlas)
 * @param resultados como máximo el {@code limite} pedido
 */
public record ResultadoBusquedaResponse(String origen, String aviso, List<ArticuloEncontradoResponse> resultados) {

    public ResultadoBusquedaResponse {
        resultados = List.copyOf(resultados);
    }
}
