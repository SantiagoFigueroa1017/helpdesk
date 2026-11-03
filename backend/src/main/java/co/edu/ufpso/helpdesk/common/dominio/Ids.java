package co.edu.ufpso.helpdesk.common.dominio;

import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.error.RecursoNoEncontradoException;

/** Conversión de los ids que llegan en la URL (acta 2.4). */
public final class Ids {

    private Ids() {
    }

    /**
     * Un id que no es un ObjectId válido responde 404, igual que uno que no existe: para quien
     * pregunta, los dos casos son "ese recurso no existe".
     */
    public static ObjectId deRuta(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new RecursoNoEncontradoException();
        }
        return new ObjectId(id);
    }

    /**
     * Id opcional que llega como filtro en la URL ({@code ?empresaId=...}). null o vacío: sin filtro.
     * Mal formado: 400 {@code PARAMETRO_INVALIDO}, porque aquí el error es del parámetro, no del recurso.
     */
    public static ObjectId deParametro(String nombre, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        if (!ObjectId.isValid(valor.strip())) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO, "El parámetro '" + nombre + "' no es un id válido.");
        }
        return new ObjectId(valor.strip());
    }

    /** Id que llega en el cuerpo, ya validado con {@link #PATRON}. */
    public static ObjectId deCuerpo(String valor) {
        return new ObjectId(valor.strip());
    }

    /** Patrón de un ObjectId en hexadecimal, para {@code @Pattern} en los DTO. */
    public static final String PATRON = "^\\s*[0-9a-fA-F]{24}\\s*$";
}
