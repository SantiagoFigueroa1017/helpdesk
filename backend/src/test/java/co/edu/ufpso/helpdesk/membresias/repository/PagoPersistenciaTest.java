package co.edu.ufpso.helpdesk.membresias.repository;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.membresias.domain.MetodoPago;
import co.edu.ufpso.helpdesk.membresias.domain.Pago;
import co.edu.ufpso.helpdesk.membresias.domain.ResultadoPago;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;

/** Guarda y lee pagos contra el validador real de {@code pagos}. */
@SpringBootTest
@ActiveProfiles("test")
class PagoPersistenciaTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T15:30:00Z");

    @Autowired
    private PagoRepository repositorio;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    private MongoCollection<Document> coleccion;

    @BeforeEach
    void copiarValidador() {
        coleccion = ValidadoresDePrueba.copiar(mongoClient, template, "pagos");
    }

    @Test
    void guardaElMontoComoDecimal128SinPerderLosCentavos() {
        Pago guardado = repositorio.save(pago(new BigDecimal("450000.75"), ResultadoPago.APROBADO));

        Document crudo = coleccion.find(eq("_id", guardado.getId())).first();
        assertThat(crudo).doesNotContainKey("_class");
        assertThat(crudo.get("monto")).isEqualTo(new Decimal128(new BigDecimal("450000.75")));
        assertThat(crudo.get("metodoPago")).isEqualTo("PSE");

        Pago leido = repositorio.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getMonto()).isEqualTo(new BigDecimal("450000.75"));
        assertThat(leido.getResultado()).isEqualTo(ResultadoPago.APROBADO);
    }

    @Test
    void elValidadorRechazaUnMontoNegativo() {
        Pago pago = pago(new BigDecimal("-1.00"), ResultadoPago.FALLIDO);
        assertThatThrownBy(() -> repositorio.save(pago)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Pago pago(BigDecimal monto, ResultadoPago resultado) {
        Pago p = new Pago();
        p.setFechaCreacion(AHORA);
        p.setFechaActualizacion(AHORA);
        p.setMembresiaId(new ObjectId());
        p.setEmpresaId(new ObjectId());
        p.setMonto(monto);
        p.setFechaPago(AHORA);
        p.setMetodoPago(MetodoPago.PSE);
        p.setReferencia("PSE-2026-118734");
        p.setResultado(resultado);
        p.setRegistradoPorId(new ObjectId());
        return p;
    }
}
