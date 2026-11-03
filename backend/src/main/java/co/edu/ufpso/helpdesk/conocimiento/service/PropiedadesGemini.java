package co.edu.ufpso.helpdesk.conocimiento.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code helpdesk.gemini.*} de {@code application.yml} (tarea 2.7).
 *
 * @param clave            clave de la API de Gemini. Sale de {@code GEMINI_API_KEY} en {@code backend/.env}.
 *                         Vacía: el backend arranca igual, los artículos quedan con el vector pendiente y la
 *                         búsqueda por significado responde con la de texto
 * @param urlBase          {@code https://generativelanguage.googleapis.com/v1beta}
 * @param tiempoConexion   espera máxima para abrir la conexión
 * @param tiempoRespuesta  espera máxima de la respuesta. Al crear o editar un artículo el usuario espera
 *                         este tiempo como máximo antes de que el artículo se guarde con el vector pendiente
 * @param loteRegeneracion artículos que la tarea programada procesa en cada corrida. El nivel gratuito
 *                         admite 30.000 tokens por minuto y un artículo recortado a 7.000 caracteres puede
 *                         gastar cerca de 2.000, así que 10 por corrida queda por debajo del límite
 */
@ConfigurationProperties("helpdesk.gemini")
public record PropiedadesGemini(String clave, String urlBase, Duration tiempoConexion, Duration tiempoRespuesta,
        int loteRegeneracion) {

    /** No muestra la clave si el record termina en un log o en un mensaje de error. */
    @Override
    public String toString() {
        return "PropiedadesGemini[clave=***, urlBase=" + urlBase + ", tiempoConexion=" + tiempoConexion
                + ", tiempoRespuesta=" + tiempoRespuesta + ", loteRegeneracion=" + loteRegeneracion + "]";
    }
}
