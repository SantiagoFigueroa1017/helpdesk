package co.edu.ufpso.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Configuracion de MongoDB.
 *
 * Spring Boot no registra un gestor de transacciones para MongoDB por su cuenta.
 * Sin este bean, @Transactional no abre ninguna transaccion y la regla de 7.5
 * (pago FALLIDO + membresia SUSPENDIDA en un solo paso) no seria atomica.
 *
 * Pendiente para 2.3: Spring Data MongoDB 5 ya no fija por defecto como se guarda
 * BigDecimal. Hay que configurarlo como DECIMAL128 o los validadores de planes,
 * pagos y tickets (COMPRAS) rechazaran los documentos. Tambien se decide ahi _class.
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}
