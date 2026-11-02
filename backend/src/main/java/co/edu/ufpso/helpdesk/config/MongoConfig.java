package co.edu.ufpso.helpdesk.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Configuracion de MongoDB.
 *
 * <p>Transacciones (2.1): Spring Boot no registra un gestor de transacciones para MongoDB por su
 * cuenta. Sin este bean, @Transactional no abre ninguna transaccion y la regla de 7.5 (pago
 * FALLIDO + membresia SUSPENDIDA en un solo paso) no seria atomica.
 *
 * <p>BigDecimal (2.3): Spring Data MongoDB 5 deja por defecto la representacion UNSPECIFIED.
 * Los validadores de planes.precio, pagos.monto y tickets.detalle.montoInvolucrado exigen
 * Decimal128 ({@code bsonType: "decimal"}), asi que se fija DECIMAL128 aqui. Boot 4.1 tambien
 * acepta la propiedad spring.data.mongodb.representation.big-decimal, pero no se usa: los
 * convertidores propios (el del detalle de tickets) se registran en este mismo bean, y un bean
 * propio de MongoCustomConversions hace que Boot ignore esa propiedad.
 *
 * <p>_class (2.3): no se escribe en ningun nivel. Cada coleccion corresponde a una sola clase y
 * el unico polimorfismo (tickets.detalle) lo discrimina el campo modulo, validado por el oneOf
 * de 6 ramas. Los validadores no admiten _class desde 2.3.
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    /**
     * Conversiones propias. Reemplaza el bean de Boot (que solo aplica la propiedad de
     * representacion de BigDecimal). En 2.3 se agrega aqui el convertidor de lectura del
     * detalle de tickets con {@code configurer.registerConverter(...)}.
     */
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(configurer ->
                configurer.bigDecimal(BigDecimalRepresentation.DECIMAL128));
    }

    /**
     * Mismo convertidor que arma Boot 4.1 (DataMongoConfiguration), con un cambio: el
     * DefaultMongoTypeMapper sin clave de tipo, para que no escriba _class.
     */
    @Bean
    MappingMongoConverter mappingMongoConverter(ObjectProvider<MongoDatabaseFactory> factory,
            MongoMappingContext context, MongoCustomConversions conversions) {
        MongoDatabaseFactory databaseFactory = factory.getIfAvailable();
        DbRefResolver dbRefResolver = (databaseFactory != null)
                ? new DefaultDbRefResolver(databaseFactory)
                : NoOpDbRefResolver.INSTANCE;

        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, context);
        converter.setCustomConversions(conversions);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }
}
