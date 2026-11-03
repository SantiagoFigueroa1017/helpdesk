package co.edu.ufpso.helpdesk.capacitaciones.mapper;

import java.util.Map;

import org.bson.types.ObjectId;

/**
 * Nombres de usuarios y empresas ya resueltos para armar respuestas de capacitaciones, solicitudes e
 * inscripciones (tarea 2.8). El servicio los busca una vez por página con {@code $in}.
 *
 * @param usuarios {@code _id → nombre}; un id ausente es un usuario eliminado
 * @param empresas {@code _id → razonSocial}
 */
public record NombresCapacitacion(Map<ObjectId, String> usuarios, Map<ObjectId, String> empresas) {

    public static final String USUARIO_ELIMINADO = "Usuario eliminado";
    public static final String EMPRESA_ELIMINADA = "Empresa eliminada";

    public NombresCapacitacion {
        usuarios = Map.copyOf(usuarios);
        empresas = Map.copyOf(empresas);
    }

    /** null si el id es null (por ejemplo, una masiva no tiene empresa). */
    public String usuario(ObjectId id) {
        return id == null ? null : usuarios.getOrDefault(id, USUARIO_ELIMINADO);
    }

    public String empresa(ObjectId id) {
        return id == null ? null : empresas.getOrDefault(id, EMPRESA_ELIMINADA);
    }
}
