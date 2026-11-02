package co.edu.ufpso.helpdesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba las dos decisiones de 2.3 sobre el convertidor que usa la aplicacion completa:
 * BigDecimal se guarda como Decimal128 y _class no se escribe en ningun nivel.
 *
 * <p>Solo convierte en memoria, no inserta nada. Igual que {@code contextLoads}, levanta el
 * contexto con el perfil test (conexion perezosa al rs0 local).
 *
 * <p>Las clases de prueba imitan los casos reales: {@code Cobro} tiene dinero como pagos.monto, y
 * {@code detalle} esta declarado como interfaz y recibe un record concreto, igual que
 * tickets.detalle. Con la configuracion por defecto, Spring Data escribiria _class en la raiz y
 * dentro del detalle, y el validador lo rechazaria (500 ESQUEMA_RECHAZADO).
 */
@SpringBootTest
@ActiveProfiles("test")
class MongoConversionesTest {

    @Autowired
    private MappingMongoConverter converter;

    @Test
    void bigDecimalSeGuardaComoDecimal128() {
        Document documento = escribir(new Cobro(new BigDecimal("1250000.50"), null));

        assertThat(documento.get("monto"))
                .isInstanceOf(Decimal128.class)
                .isEqualTo(new Decimal128(new BigDecimal("1250000.50")));
    }

    @Test
    void decimal128SeLeeComoBigDecimalSinPerderEscala() {
        Document documento = new Document("monto", new Decimal128(new BigDecimal("450000.00")));

        Cobro leido = converter.read(Cobro.class, documento);

        assertThat(leido.monto()).isEqualTo(new BigDecimal("450000.00"));
    }

    @Test
    void noEscribeClassEnLaRaiz() {
        Document documento = escribir(new Cobro(new BigDecimal("1"), null));

        assertThat(documento).doesNotContainKey("_class");
        assertThat(converter.getTypeMapper().isTypeKey("_class")).isFalse();
    }

    @Test
    void noEscribeClassEnUnEmbebidoPolimorfico() {
        Document documento = escribir(new Cobro(new BigDecimal("1"),
                new DetalleCompra("900123456-1", new BigDecimal("98000.25"))));

        Document detalle = documento.get("detalle", Document.class);
        assertThat(detalle).doesNotContainKey("_class");
        assertThat(detalle.keySet()).containsExactlyInAnyOrder("nitProveedor", "montoInvolucrado");
        assertThat(detalle.get("montoInvolucrado")).isInstanceOf(Decimal128.class);
    }

    private Document escribir(Object entidad) {
        Document documento = new Document();
        converter.write(entidad, documento);
        return documento;
    }

    /** Declarado como interfaz, igual que DetalleTicket en tickets. */
    interface Detalle {
    }

    record DetalleCompra(String nitProveedor, BigDecimal montoInvolucrado) implements Detalle {
    }

    record Cobro(BigDecimal monto, Detalle detalle) {
    }
}
