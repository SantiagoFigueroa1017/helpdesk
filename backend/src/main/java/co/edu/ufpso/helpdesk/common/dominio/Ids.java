package co.edu.ufpso.helpdesk.common.dominio;

import org.bson.types.ObjectId;

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
}
