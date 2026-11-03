package co.edu.ufpso.helpdesk.reportes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Forma de los pipelines A1 a A9 sin MongoDB (tarea 2.9): etapas en el orden de los scripts de
 * {@code database/reportes/} y las piezas que deciden qué índice se usa. Los resultados contra datos reales los
 * prueba {@code ReportesIntegracionTest}.
 */
class PipelinesReportesTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T12:00:00Z");

    private static List<String> etapas(List<Document> pipeline) {
        return pipeline.stream().map(d -> d.keySet().iterator().next()).toList();
    }

    @Test
    void cadaReporteUsaLasEtapasDelDocumentoDeRequisitos() {
        assertThat(etapas(PipelinesReportes.a1IncidentesPorModulo(null, null)))
                .containsExactly("$match", "$group", "$project", "$sort");
        assertThat(etapas(PipelinesReportes.a2MembresiasActivasPorPlan(AHORA)))
                .containsExactly("$lookup", "$project", "$sort");
        assertThat(etapas(PipelinesReportes.a3CargaPorAgente(List.of(new ObjectId()))))
                .containsExactly("$match", "$lookup", "$project", "$sort");
        assertThat(etapas(PipelinesReportes.a4PlanYTicketsPorEmpresa(AHORA, 0, 20)))
                .containsExactly("$lookup", "$lookup", "$project", "$sort", "$skip", "$limit");
        assertThat(etapas(PipelinesReportes.a5RecaudoMensual(PipelinesReportes.a5Filtro(null, AHORA, AHORA))))
                .containsExactly("$match", "$group", "$sort");
        assertThat(etapas(PipelinesReportes.a6AsistenciaPorCapacitacion(new Document(), AHORA, 0, 20)))
                .containsExactly("$sort", "$skip", "$limit", "$lookup", "$project", "$project");
        assertThat(etapas(PipelinesReportes.a7ArticulosPorModulo()))
                .containsExactly("$lookup", "$group", "$project");
        assertThat(etapas(PipelinesReportes.a8SuspendidasConAbiertos()))
                .containsExactly("$match", "$lookup", "$lookup", "$lookup", "$project", "$match");
        assertThat(etapas(PipelinesReportes.a9ImpactoCapacitaciones(90, AHORA, AHORA)))
                .containsExactly("$match", "$lookup", "$match", "$lookup", "$project", "$group", "$project", "$sort");
    }

    @Test
    void a1FiltraLosCincoModulosParaUsarElIndice() {
        Document match = PipelinesReportes.a1IncidentesPorModulo(null, null).get(0).get("$match", Document.class);
        assertThat(match.get("modulo", Document.class).getList("$in", String.class))
                .containsExactly("VENTAS_ONLINE", "INVENTARIOS", "COMPRAS", "PRODUCCION", "VENTAS_EN_SITIO");
        assertThat(match).doesNotContainKey("fechaCreacion");

        Document conFechas = PipelinesReportes.a1IncidentesPorModulo(AHORA, AHORA.plusSeconds(60)).get(0)
                .get("$match", Document.class);
        assertThat(conFechas.get("fechaCreacion", Document.class)).containsOnlyKeys("$gte", "$lt");
    }

    @Test
    void a5EntraPorElIndiceQueCorresponde() {
        ObjectId empresa = new ObjectId();
        assertThat(PipelinesReportes.a5Filtro(empresa, AHORA, AHORA)).containsOnlyKeys("empresaId", "fechaPago");
        assertThat(PipelinesReportes.a5Filtro(null, AHORA, AHORA)).containsOnlyKeys("resultado", "fechaPago");
    }

    @Test
    void a6AgregaElMatchSoloConFiltros() {
        Document filtro = PipelinesReportes.a6Filtro(null, null, ModuloErp.COMPRAS);
        assertThat(etapas(PipelinesReportes.a6AsistenciaPorCapacitacion(filtro, AHORA, 40, 20)))
                .containsExactly("$match", "$sort", "$skip", "$limit", "$lookup", "$project", "$project");
        assertThat(PipelinesReportes.a6Filtro(null, null, null)).isEmpty();
    }

    @Test
    void a9UsaLaVentanaEnMilisegundos() {
        Document lookup = PipelinesReportes.a9ImpactoCapacitaciones(30, AHORA, AHORA).get(3).get("$lookup", Document.class);
        String json = lookup.toJson();
        assertThat(json).contains(String.valueOf(30L * 24 * 3600 * 1000));
        assertThat(lookup.getString("from")).isEqualTo("tickets");
    }
}
