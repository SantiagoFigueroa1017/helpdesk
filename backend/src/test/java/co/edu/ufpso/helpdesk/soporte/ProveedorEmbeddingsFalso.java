package co.edu.ufpso.helpdesk.soporte;

import java.util.ArrayList;
import java.util.List;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.service.EmbeddingNoDisponibleException;
import co.edu.ufpso.helpdesk.conocimiento.service.GeminiEmbeddings;
import co.edu.ufpso.helpdesk.conocimiento.service.ProveedorEmbeddings;

/**
 * Reemplaza a Gemini en las pruebas (tarea 2.7): no gasta cuota y puede simular fallas. Devuelve un vector
 * de 768 valores que depende del texto, con el mismo nombre de modelo que Gemini.
 */
public class ProveedorEmbeddingsFalso implements ProveedorEmbeddings {

    private EmbeddingNoDisponibleException.Motivo falla;
    private Runnable antesDeResponder = () -> { };
    private final List<String> documentos = new ArrayList<>();
    private final List<String> consultas = new ArrayList<>();

    /** Deja el proveedor funcionando y sin llamadas registradas. Se llama antes de cada prueba. */
    public void reiniciar() {
        falla = null;
        antesDeResponder = () -> { };
        documentos.clear();
        consultas.clear();
    }

    /** Desde ahora, cada llamada falla con ese motivo. */
    public void fallarCon(EmbeddingNoDisponibleException.Motivo motivo) {
        this.falla = motivo;
    }

    /** Acción que corre en medio de la llamada, para simular una edición mientras Gemini responde. */
    public void antesDeResponder(Runnable accion) {
        this.antesDeResponder = accion;
    }

    /** Textos que se convirtieron como artículos ({@code RETRIEVAL_DOCUMENT}). */
    public List<String> documentos() {
        return documentos;
    }

    /** Textos que se convirtieron como búsquedas ({@code RETRIEVAL_QUERY}). */
    public List<String> consultas() {
        return consultas;
    }

    @Override
    public String modelo() {
        return GeminiEmbeddings.MODELO;
    }

    @Override
    public boolean disponible() {
        return falla != EmbeddingNoDisponibleException.Motivo.NO_CONFIGURADO;
    }

    @Override
    public List<Double> documento(String texto) {
        documentos.add(texto);
        return responder(texto);
    }

    @Override
    public List<Double> consulta(String texto) {
        consultas.add(texto);
        return responder(texto);
    }

    private List<Double> responder(String texto) {
        antesDeResponder.run();
        if (falla != null) {
            throw new EmbeddingNoDisponibleException(falla, "Falla simulada: " + falla);
        }
        return vector(texto);
    }

    /** Vector de prueba: cambia con el texto y nunca trae enteros sueltos. */
    public static List<Double> vector(String texto) {
        int semilla = texto.hashCode();
        List<Double> v = new ArrayList<>(Articulo.DIMENSIONES);
        for (int i = 0; i < Articulo.DIMENSIONES; i++) {
            v.add(Math.sin(semilla + i) / 10 + 0.0001);
        }
        return v;
    }
}
