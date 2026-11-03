package co.edu.ufpso.helpdesk.soporte;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;

/**
 * Copia el validador {@code $jsonSchema} de una colección de {@code helpdesk} (replica set local) a
 * la base de pruebas, igual que los scripts de {@code database/pruebas/} con
 * {@code helpdesk_pruebas}. Así las pruebas de persistencia escriben contra el validador real.
 *
 * <p>Requisito: haber corrido {@code database/validadores/00_todos_los_validadores.js} en el local.
 */
public final class ValidadoresDePrueba {

    private ValidadoresDePrueba() {
    }

    /**
     * Borra la colección en la base de pruebas y la crea vacía con el validador de {@code helpdesk}.
     *
     * @return la colección recién creada, para leer los documentos crudos
     */
    public static MongoCollection<Document> copiar(MongoClient cliente, MongoTemplate pruebas, String coleccion) {
        Document validador = validadorDe(cliente, coleccion);
        pruebas.getDb().getCollection(coleccion).drop();
        pruebas.getDb().createCollection(coleccion, new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                        .validator(validador)
                        .validationLevel(ValidationLevel.STRICT)
                        .validationAction(ValidationAction.ERROR)));
        return pruebas.getDb().getCollection(coleccion);
    }

    /**
     * Copia los índices de una colección de {@code helpdesk} a la base de pruebas, con sus mismos nombres
     * y opciones (tarea 2.7). Hace falta para las consultas que no funcionan sin índice, como {@code $text}.
     * Se llama después de {@link #copiar}, que deja la colección vacía y solo con {@code _id_}.
     *
     * <p>Requisito: haber corrido {@code database/indices/00_indices.js} en el local.
     *
     * @param esperados nombres que deben existir en {@code helpdesk}; si falta uno, la prueba avisa qué script correr
     */
    public static void copiarIndices(MongoClient cliente, MongoTemplate pruebas, String coleccion, String... esperados) {
        List<Document> especificaciones = new ArrayList<>();
        for (Document indice : cliente.getDatabase("helpdesk").getCollection(coleccion).listIndexes()) {
            if ("_id_".equals(indice.getString("name"))) {
                continue;
            }
            Document especificacion = new Document(indice);
            especificacion.remove("v");
            especificacion.remove("ns");
            especificaciones.add(especificacion);
        }
        List<String> nombres = especificaciones.stream().map(i -> i.getString("name")).toList();
        assertThat(nombres)
                .as("Faltan índices en helpdesk.%s del local. Corre database/indices/00_indices.js", coleccion)
                .contains(esperados);
        pruebas.getDb().runCommand(new Document("createIndexes", coleccion).append("indexes", especificaciones));
    }

    /** El documento {@code validator} de una colección de {@code helpdesk}. */
    public static Document validadorDe(MongoClient cliente, String coleccion) {
        Document info = cliente.getDatabase("helpdesk").listCollections()
                .filter(eq("name", coleccion)).first();
        assertThat(info)
                .as("Falta helpdesk.%s en el local. Corre database/validadores/00_todos_los_validadores.js", coleccion)
                .isNotNull();
        return info.get("options", Document.class).get("validator", Document.class);
    }
}
