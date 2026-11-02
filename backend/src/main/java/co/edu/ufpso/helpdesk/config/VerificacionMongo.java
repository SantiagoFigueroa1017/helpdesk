package co.edu.ufpso.helpdesk.config;

import java.util.Set;
import java.util.TreeSet;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Comprobacion de arranque (tarea 2.1). Al iniciar la aplicacion:
 * 1. Lee la version del servidor y el nombre del replica set.
 * 2. Cuenta las colecciones de la base helpdesk (deben ser 13).
 * 3. Abre una transaccion y lee la coleccion roles dentro de ella.
 *
 * Solo lee. No escribe nada en la base. Nunca imprime la cadena de conexion.
 * No corre en el perfil test.
 */
@Component
@Profile("!test")
class VerificacionMongo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificacionMongo.class);
    private static final int COLECCIONES_ESPERADAS = 13;

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;

    VerificacionMongo(MongoTemplate mongoTemplate, PlatformTransactionManager transactionManager) {
        this.mongoTemplate = mongoTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        Document buildInfo = mongoTemplate.executeCommand(new Document("buildInfo", 1));
        Document hello = mongoTemplate.executeCommand(new Document("hello", 1));

        String version = buildInfo.getString("version");
        String replicaSet = hello.getString("setName");
        String baseDatos = mongoTemplate.getDb().getName();

        log.info("MongoDB {} | replica set: {} | base de datos: {}", version, replicaSet, baseDatos);

        if (replicaSet == null) {
            throw new IllegalStateException(
                    "El servidor no es un replica set. Las transacciones multi-documento no funcionarian.");
        }

        Set<String> colecciones = new TreeSet<>(mongoTemplate.getCollectionNames());
        if (colecciones.size() == COLECCIONES_ESPERADAS) {
            log.info("Colecciones: {} de {}", colecciones.size(), COLECCIONES_ESPERADAS);
        } else {
            log.warn("Colecciones: {} de {}. Encontradas: {}. Revisar database/validadores/00_todos_los_validadores.js",
                    colecciones.size(), COLECCIONES_ESPERADAS, colecciones);
        }

        Long roles = transactionTemplate.execute(estado -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("No se abrio la transaccion. Revisar MongoConfig.");
            }
            return mongoTemplate.count(new Query(), "roles");
        });

        log.info("Transaccion de prueba: OK ({} roles leidos dentro de la transaccion)", roles);
    }
}
