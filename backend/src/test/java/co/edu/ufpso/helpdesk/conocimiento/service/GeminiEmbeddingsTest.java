package co.edu.ufpso.helpdesk.conocimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import co.edu.ufpso.helpdesk.conocimiento.service.EmbeddingNoDisponibleException.Motivo;

/**
 * Cliente de Gemini contra un servidor HTTP falso del JDK (tarea 2.7): qué envía y cómo lee la respuesta,
 * sin Spring y sin gastar cuota.
 */
class GeminiEmbeddingsTest {

    private HttpServer servidor;
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> clave = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private volatile int estado = 200;
    private volatile String respuesta = "{}";

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            ruta.set(intercambio.getRequestURI().getPath());
            clave.set(intercambio.getRequestHeaders().getFirst("x-goog-api-key"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    private GeminiEmbeddings cliente(String claveApi) {
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta";
        return new GeminiEmbeddings(new PropiedadesGemini(claveApi, url, Duration.ofSeconds(2), Duration.ofSeconds(2), 10));
    }

    private static String vectorJson(int dimensiones) {
        StringJoiner valores = new StringJoiner(",", "[", "]");
        for (int i = 0; i < dimensiones; i++) {
            valores.add(String.valueOf(i == 0 ? 0 : i / 1000.0));
        }
        return "{\"embedding\":{\"values\":" + valores + "}}";
    }

    @Test
    void enviaElModeloLaTareaYLasDimensionesConLaClaveEnLaCabecera() {
        respuesta = vectorJson(768);
        List<Double> vector = cliente(" clave-de-prueba ").documento("El datáfono no conecta");

        assertThat(vector).hasSize(768).first().isEqualTo(0.0);
        assertThat(ruta.get()).isEqualTo("/v1beta/models/gemini-embedding-001:embedContent");
        assertThat(clave.get()).isEqualTo("clave-de-prueba");
        assertThat(cuerpo.get()).contains("\"model\":\"models/gemini-embedding-001\"")
                .contains("\"taskType\":\"RETRIEVAL_DOCUMENT\"")
                .contains("\"outputDimensionality\":768")
                .contains("\"text\":\"El datáfono no conecta\"")
                .doesNotContain("clave-de-prueba");
    }

    @Test
    void laConsultaUsaRetrievalQuery() {
        respuesta = vectorJson(768);
        cliente("clave").consulta("no imprime la factura");
        assertThat(cuerpo.get()).contains("\"taskType\":\"RETRIEVAL_QUERY\"");
    }

    @Test
    void un429SeInformaComoLimiteAlcanzado() {
        estado = 429;
        respuesta = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}";
        assertThatThrownBy(() -> cliente("clave").documento("texto"))
                .isInstanceOf(EmbeddingNoDisponibleException.class)
                .extracting(e -> ((EmbeddingNoDisponibleException) e).getMotivo())
                .isEqualTo(Motivo.LIMITE_ALCANZADO);
    }

    @Test
    void otroErrorOUnVectorIncompletoSeInformanComoError() {
        estado = 400;
        respuesta = "{\"error\":{\"code\":400}}";
        assertThatThrownBy(() -> cliente("clave").documento("texto"))
                .extracting(e -> ((EmbeddingNoDisponibleException) e).getMotivo()).isEqualTo(Motivo.ERROR);

        estado = 200;
        respuesta = vectorJson(767);
        assertThatThrownBy(() -> cliente("clave").documento("texto"))
                .extracting(e -> ((EmbeddingNoDisponibleException) e).getMotivo()).isEqualTo(Motivo.ERROR);
    }

    @Test
    void sinClaveNoLlamaAGemini() {
        GeminiEmbeddings sinClave = cliente("  ");
        assertThat(sinClave.disponible()).isFalse();
        assertThatThrownBy(() -> sinClave.documento("texto"))
                .extracting(e -> ((EmbeddingNoDisponibleException) e).getMotivo()).isEqualTo(Motivo.NO_CONFIGURADO);
        assertThat(ruta.get()).isNull();
    }

    @Test
    void elToStringDeLasPropiedadesNoMuestraLaClave() {
        assertThat(new PropiedadesGemini("secreta", "u", Duration.ZERO, Duration.ZERO, 1).toString())
                .doesNotContain("secreta");
    }
}
