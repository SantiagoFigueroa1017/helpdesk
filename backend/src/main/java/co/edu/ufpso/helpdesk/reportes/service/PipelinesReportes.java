package co.edu.ufpso.helpdesk.reportes.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;

/**
 * Los pipelines de A1 a A9 (tarea 2.9), armados con {@link Document} del driver tal como están en
 * {@code database/reportes/}. Cada método dice en qué se aparta del script y por qué.
 *
 * <p>Solo arma los pipelines: no consulta la base. Así {@code PipelinesReportesTest} los compara con los
 * scripts sin MongoDB, y la sustentación puede mostrar el mismo pipeline en mongosh y en Java.
 *
 * <p>En los reportes, abierto = ABIERTO o EN_PROCESO y cerrado = RESUELTO o CERRADO (contexto 4.2).
 */
public final class PipelinesReportes {

    public static final List<String> MODULOS = Arrays.stream(ModuloErp.values()).map(Enum::name).toList();
    public static final List<String> ABIERTOS = List.of("ABIERTO", "EN_PROCESO");
    public static final List<String> CERRADOS = List.of("RESUELTO", "CERRADO");
    public static final String ZONA = "America/Bogota";
    private static final long MS_POR_DIA = 24L * 3600 * 1000;

    private PipelinesReportes() {
    }

    // ------------------------------------------------------------------
    // A1 · Incidentes y tiempo promedio de resolución por módulo (tickets)
    // ------------------------------------------------------------------

    /**
     * A1, igual que el script. El {@code $match} lleva los 5 módulos en {@code $in} para que entre
     * {@code ix_tickets_modulo_tipo_fecha}. Sin {@code desde} ni {@code hasta} no filtra por fecha.
     *
     * <pre>
     * db.tickets.aggregate([
     *   { $match: { modulo: { $in: [5 módulos] }, tipo: "INCIDENTE", fechaCreacion: { $gte: desde, $lt: hasta } } },
     *   { $group: { _id: "$modulo", incidentes: { $sum: 1 }, abiertos: ..., cerrados: ...,
     *               msPromedio: { $avg: fechaResolucion - fechaCreacion, solo si hay fechaResolucion } } },
     *   { $project: { _id: 0, modulo: "$_id", ..., horasPromedioResolucion, porcentajeCerrados } },
     *   { $sort: { incidentes: -1, modulo: 1 } }
     * ])
     * </pre>
     */
    public static List<Document> a1IncidentesPorModulo(Instant desde, Instant hastaExclusivo) {
        Document filtro = new Document("modulo", new Document("$in", MODULOS)).append("tipo", "INCIDENTE");
        if (desde != null || hastaExclusivo != null) {
            Document rango = new Document();
            if (desde != null) {
                rango.append("$gte", Date.from(desde));
            }
            if (hastaExclusivo != null) {
                rango.append("$lt", Date.from(hastaExclusivo));
            }
            filtro.append("fechaCreacion", rango);
        }
        return List.of(
                new Document("$match", filtro),
                new Document("$group", new Document("_id", "$modulo")
                        .append("incidentes", new Document("$sum", 1))
                        .append("abiertos", contarSi(new Document("$in", List.of("$estado", ABIERTOS))))
                        .append("cerrados", contarSi(new Document("$in", List.of("$estado", CERRADOS))))
                        .append("msPromedio", new Document("$avg", new Document("$cond", Arrays.asList(
                                new Document("$ne", Arrays.asList(
                                        new Document("$ifNull", Arrays.asList("$fechaResolucion", null)), null)),
                                new Document("$subtract", List.of("$fechaResolucion", "$fechaCreacion")),
                                null))))),
                new Document("$project", new Document("_id", 0)
                        .append("modulo", "$_id")
                        .append("incidentes", 1)
                        .append("abiertos", 1)
                        .append("cerrados", 1)
                        .append("horasPromedioResolucion", redondear(
                                new Document("$divide", List.of("$msPromedio", 3600000)), 1))
                        .append("porcentajeCerrados", redondear(new Document("$multiply", List.of(
                                new Document("$divide", List.of("$cerrados", "$incidentes")), 100)), 1))),
                new Document("$sort", new Document("incidentes", -1).append("modulo", 1)));
    }

    // ------------------------------------------------------------------
    // A2 · Empresas con membresía activa por plan (planes)
    // ------------------------------------------------------------------

    /**
     * A2, igual que el script, con {@code planId} en la fila para que el frontend enlace el plan.
     *
     * <pre>
     * db.planes.aggregate([
     *   { $lookup: { from: "membresias", localField: "_id", foreignField: "planId", pipeline: [
     *       { $match: { estado: "ACTIVA" } },
     *       { $lookup: { from: "empresas", localField: "empresaId", foreignField: "_id",
     *                    pipeline: [{ $project: { _id: 0, razonSocial: 1 } }], as: "empresa" } },
     *       { $project: { _id: 0, razonSocial: { $first: "$empresa.razonSocial" }, fechaVencimiento: 1,
     *                     vigente: { $lte: [ahora, "$fechaVencimiento"] } } } ], as: "activas" } },
     *   { $project: { _id: 0, planId: "$_id", plan: "$nombre", ..., empresasActivas, vigentes, vencidas, empresas } },
     *   { $sort: { empresasActivas: -1, plan: 1 } }
     * ])
     * </pre>
     */
    public static List<Document> a2MembresiasActivasPorPlan(Instant ahora) {
        return List.of(
                new Document("$lookup", new Document("from", "membresias")
                        .append("localField", "_id")
                        .append("foreignField", "planId")
                        .append("pipeline", List.of(
                                new Document("$match", new Document("estado", "ACTIVA")),
                                new Document("$lookup", new Document("from", "empresas")
                                        .append("localField", "empresaId")
                                        .append("foreignField", "_id")
                                        .append("pipeline", List.of(new Document("$project",
                                                new Document("_id", 0).append("razonSocial", 1))))
                                        .append("as", "empresa")),
                                new Document("$project", new Document("_id", 0)
                                        .append("razonSocial", new Document("$first", "$empresa.razonSocial"))
                                        .append("fechaVencimiento", 1)
                                        .append("vigente", new Document("$lte",
                                                List.of(Date.from(ahora), "$fechaVencimiento"))))))
                        .append("as", "activas")),
                new Document("$project", new Document("_id", 0)
                        .append("planId", "$_id")
                        .append("plan", "$nombre")
                        .append("nivelAcceso", 1)
                        .append("precio", 1)
                        .append("activo", 1)
                        .append("empresasActivas", new Document("$size", "$activas"))
                        .append("vigentes", new Document("$size", new Document("$filter",
                                new Document("input", "$activas").append("cond", "$$this.vigente"))))
                        .append("vencidas", new Document("$size", new Document("$filter",
                                new Document("input", "$activas").append("cond",
                                        new Document("$not", List.of("$$this.vigente"))))))
                        .append("empresas", new Document("$sortArray",
                                new Document("input", "$activas.razonSocial").append("sortBy", 1)))),
                new Document("$sort", new Document("empresasActivas", -1).append("plan", 1)));
    }

    // ------------------------------------------------------------------
    // A3 · Carga de tickets abiertos por agente (usuarios)
    // ------------------------------------------------------------------

    /**
     * A3, igual que el script, con {@code usuarioId} en la fila. {@code rolesAgente} son los roles GLOBAL
     * activos con {@code TICKETS_ASIGNAR} (definición de agente de 1.6); el servicio los lee antes.
     *
     * <pre>
     * db.usuarios.aggregate([
     *   { $match: { rolIds: { $in: rolesAgente } } },
     *   { $lookup: { from: "tickets", localField: "_id", foreignField: "agenteId", pipeline: [
     *       { $match: { estado: { $in: ["ABIERTO", "EN_PROCESO"] } } },
     *       { $project: { _id: 0, estado: 1, fechaCreacion: 1 } } ], as: "abiertos" } },
     *   { $project: { _id: 0, usuarioId: "$_id", nombre: 1, email: 1, activo: 1, abiertos: { $size: "$abiertos" },
     *                 enProceso: ..., masAntiguo: { $min: "$abiertos.fechaCreacion" } } },
     *   { $sort: { abiertos: -1, masAntiguo: 1, nombre: 1 } }
     * ])
     * </pre>
     */
    public static List<Document> a3CargaPorAgente(List<ObjectId> rolesAgente) {
        return List.of(
                new Document("$match", new Document("rolIds", new Document("$in", rolesAgente))),
                new Document("$lookup", new Document("from", "tickets")
                        .append("localField", "_id")
                        .append("foreignField", "agenteId")
                        .append("pipeline", List.of(
                                new Document("$match", new Document("estado", new Document("$in", ABIERTOS))),
                                new Document("$project", new Document("_id", 0).append("estado", 1)
                                        .append("fechaCreacion", 1))))
                        .append("as", "abiertos")),
                new Document("$project", new Document("_id", 0)
                        .append("usuarioId", "$_id")
                        .append("nombre", 1)
                        .append("email", 1)
                        .append("activo", 1)
                        .append("abiertos", new Document("$size", "$abiertos"))
                        .append("enProceso", new Document("$size", new Document("$filter",
                                new Document("input", "$abiertos").append("cond",
                                        new Document("$eq", List.of("$$this.estado", "EN_PROCESO"))))))
                        .append("masAntiguo", new Document("$min", "$abiertos.fechaCreacion"))),
                new Document("$sort", new Document("abiertos", -1).append("masAntiguo", 1).append("nombre", 1)));
    }

    // ------------------------------------------------------------------
    // A4 · Plan contratado y tickets por empresa (empresas)
    // ------------------------------------------------------------------

    /**
     * A4, igual que el script, con {@code empresaId} en la fila y paginado (decidido en 2.9): {@code $skip} y
     * {@code $limit} van al final porque el orden depende de los totales que calcula el {@code $lookup}.
     * {@code empresaId} desempata el orden para que las páginas no se crucen.
     *
     * <pre>
     * db.empresas.aggregate([
     *   { $lookup: { from: "membresias", localField: "_id", foreignField: "empresaId", pipeline: [
     *       { $lookup: { from: "planes", ... as: "plan" } },
     *       { $project: { _id: 0, estado: 1, fechaVencimiento: 1, modulosContratados: 1, plan: { $first: "$plan" } } } ],
     *       as: "membresia" } },
     *   { $lookup: { from: "tickets", localField: "_id", foreignField: "empresaId", pipeline: [
     *       { $group: { _id: null, total: { $sum: 1 }, abiertos: ..., incidentes: ..., sugerencias: ... } } ],
     *       as: "tickets" } },
     *   { $project: { _id: 0, empresaId: "$_id", nit: 1, razonSocial: 1, plan, estadoMembresia (VENCIDA derivada),
     *                 modulos, tickets, abiertos, incidentes, sugerencias } },
     *   { $sort: { tickets: -1, razonSocial: 1, empresaId: 1 } },
     *   { $skip: pagina * tamano }, { $limit: tamano }
     * ])
     * </pre>
     */
    public static List<Document> a4PlanYTicketsPorEmpresa(Instant ahora, long saltar, int tamano) {
        Document m = new Document("$first", "$membresia");
        return List.of(
                new Document("$lookup", new Document("from", "membresias")
                        .append("localField", "_id")
                        .append("foreignField", "empresaId")
                        .append("pipeline", List.of(
                                new Document("$lookup", new Document("from", "planes")
                                        .append("localField", "planId")
                                        .append("foreignField", "_id")
                                        .append("pipeline", List.of(new Document("$project",
                                                new Document("_id", 0).append("nombre", 1).append("nivelAcceso", 1))))
                                        .append("as", "plan")),
                                new Document("$project", new Document("_id", 0)
                                        .append("estado", 1)
                                        .append("fechaVencimiento", 1)
                                        .append("modulosContratados", 1)
                                        .append("plan", new Document("$first", "$plan")))))
                        .append("as", "membresia")),
                new Document("$lookup", new Document("from", "tickets")
                        .append("localField", "_id")
                        .append("foreignField", "empresaId")
                        .append("pipeline", List.of(new Document("$group", new Document("_id", null)
                                .append("total", new Document("$sum", 1))
                                .append("abiertos", contarSi(new Document("$in", List.of("$estado", ABIERTOS))))
                                .append("incidentes", contarSi(new Document("$eq", List.of("$tipo", "INCIDENTE"))))
                                .append("sugerencias", contarSi(new Document("$eq", List.of("$tipo", "SUGERENCIA")))))))
                        .append("as", "tickets")),
                new Document("$project", new Document("_id", 0)
                        .append("empresaId", "$_id")
                        .append("nit", 1)
                        .append("razonSocial", 1)
                        .append("plan", new Document("$ifNull",
                                List.of(new Document("$first", "$membresia.plan.nombre"), "Sin membresía")))
                        .append("estadoMembresia", new Document("$let", new Document("vars", new Document("m", m))
                                .append("in", new Document("$cond", List.of(
                                        new Document("$eq", Arrays.asList(new Document("$ifNull",
                                                Arrays.asList("$$m", null)), null)),
                                        "-",
                                        new Document("$cond", List.of(
                                                new Document("$and", List.of(
                                                        new Document("$eq", List.of("$$m.estado", "ACTIVA")),
                                                        new Document("$lt", List.of("$$m.fechaVencimiento",
                                                                Date.from(ahora))))),
                                                "VENCIDA",
                                                "$$m.estado")))))))
                        .append("modulos", new Document("$size", new Document("$ifNull",
                                List.of(new Document("$first", "$membresia.modulosContratados"), List.of()))))
                        .append("tickets", primeroOCero("$tickets.total"))
                        .append("abiertos", primeroOCero("$tickets.abiertos"))
                        .append("incidentes", primeroOCero("$tickets.incidentes"))
                        .append("sugerencias", primeroOCero("$tickets.sugerencias"))),
                new Document("$sort", new Document("tickets", -1).append("razonSocial", 1).append("empresaId", 1)),
                new Document("$skip", saltar),
                new Document("$limit", tamano));
    }

    // ------------------------------------------------------------------
    // A5 · Recaudo y pagos fallidos por mes (pagos)
    // ------------------------------------------------------------------

    /**
     * El {@code $match} de A5, igual que el script: con empresa entra por {@code ix_pagos_empresa_fecha}; sin
     * ella, {@code resultado} con los dos valores hace que entre {@code ix_pagos_resultado_fecha}.
     */
    public static Document a5Filtro(ObjectId empresaId, Instant desde, Instant hastaExclusivo) {
        Document rango = new Document("$gte", Date.from(desde)).append("$lt", Date.from(hastaExclusivo));
        if (empresaId != null) {
            return new Document("empresaId", empresaId).append("fechaPago", rango);
        }
        return new Document("resultado", new Document("$in", List.of("APROBADO", "FALLIDO"))).append("fechaPago", rango);
    }

    /**
     * A5, igual que el script. Los meses se cuentan en hora de Bogotá y el dinero se suma como Decimal128.
     *
     * <pre>
     * db.pagos.aggregate([
     *   { $match: filtro },
     *   { $group: { _id: { $dateToString: { format: "%Y-%m", date: "$fechaPago", timezone: "America/Bogota" } },
     *               recaudo, aprobados, fallidos, montoFallido } },
     *   { $sort: { _id: 1 } }
     * ])
     * </pre>
     */
    public static List<Document> a5RecaudoMensual(Document filtro) {
        Decimal128 cero = new Decimal128(BigDecimal.ZERO);
        Document aprobado = new Document("$eq", List.of("$resultado", "APROBADO"));
        Document fallido = new Document("$eq", List.of("$resultado", "FALLIDO"));
        return List.of(
                new Document("$match", filtro),
                new Document("$group", new Document("_id", new Document("$dateToString",
                        new Document("format", "%Y-%m").append("date", "$fechaPago").append("timezone", ZONA)))
                        .append("recaudo", new Document("$sum", new Document("$cond", List.of(aprobado, "$monto", cero))))
                        .append("aprobados", contarSi(aprobado))
                        .append("fallidos", contarSi(fallido))
                        .append("montoFallido", new Document("$sum",
                                new Document("$cond", List.of(fallido, "$monto", cero))))),
                new Document("$sort", new Document("_id", 1)));
    }

    // ------------------------------------------------------------------
    // A6 · Porcentaje de asistencia por capacitación (capacitaciones)
    // ------------------------------------------------------------------

    /** Filtro opcional de A6 por fecha de inicio y módulo. Vacío si no llegan filtros. */
    public static Document a6Filtro(Instant desde, Instant hastaExclusivo, ModuloErp modulo) {
        Document filtro = new Document();
        if (desde != null || hastaExclusivo != null) {
            Document rango = new Document();
            if (desde != null) {
                rango.append("$gte", Date.from(desde));
            }
            if (hastaExclusivo != null) {
                rango.append("$lt", Date.from(hastaExclusivo));
            }
            filtro.append("fechaInicio", rango);
        }
        if (modulo != null) {
            filtro.append("modulo", modulo.name());
        }
        return filtro;
    }

    /**
     * A6 con las etapas del script ({@code $lookup}, {@code $project}, {@code $sort}) y paginado (decidido en
     * 2.9). El orden va antes del {@code $lookup}: depende de {@code fechaInicio}, que está guardado, así
     * MongoDB solo busca las inscripciones de las capacitaciones de la página. El {@code $match} aparece
     * solo con filtros.
     *
     * <pre>
     * db.capacitaciones.aggregate([
     *   { $match: filtro },                                    // solo con filtros
     *   { $sort: { fechaInicio: -1, _id: -1 } }, { $skip: n }, { $limit: tamano },
     *   { $lookup: { from: "inscripciones", localField: "_id", foreignField: "capacitacionId",
     *                pipeline: [{ $project: { _id: 0, asistio: 1 } }], as: "inscripciones" } },
     *   { $project: { _id: 0, capacitacionId: "$_id", titulo: 1, ..., realizada, inscritos, asistieron, marcadas } },
     *   { $project: { ..., porcentajeAsistencia: asistieron / marcadas × 100 (null sin marcas) } }
     * ])
     * </pre>
     */
    public static List<Document> a6AsistenciaPorCapacitacion(Document filtro, Instant ahora, long saltar, int tamano) {
        List<Document> pipeline = new ArrayList<>();
        if (!filtro.isEmpty()) {
            pipeline.add(new Document("$match", filtro));
        }
        pipeline.add(new Document("$sort", new Document("fechaInicio", -1).append("_id", -1)));
        pipeline.add(new Document("$skip", saltar));
        pipeline.add(new Document("$limit", tamano));
        pipeline.add(new Document("$lookup", new Document("from", "inscripciones")
                .append("localField", "_id")
                .append("foreignField", "capacitacionId")
                .append("pipeline", List.of(new Document("$project", new Document("_id", 0).append("asistio", 1))))
                .append("as", "inscripciones")));
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("capacitacionId", "$_id")
                .append("titulo", 1)
                .append("tipo", 1)
                .append("modulo", 1)
                .append("fechaInicio", 1)
                .append("estado", 1)
                .append("cupo", 1)
                .append("cuposOcupados", 1)
                .append("realizada", new Document("$and", List.of(
                        new Document("$eq", List.of("$estado", "PROGRAMADA")),
                        new Document("$lt", List.of("$fechaFin", Date.from(ahora))))))
                .append("inscritos", new Document("$size", "$inscripciones"))
                .append("asistieron", new Document("$size", new Document("$filter",
                        new Document("input", "$inscripciones").append("cond",
                                new Document("$eq", List.of("$$this.asistio", true))))))
                .append("marcadas", new Document("$size", new Document("$filter",
                        new Document("input", "$inscripciones").append("cond",
                                new Document("$ne", Arrays.asList("$$this.asistio", null))))))));
        pipeline.add(new Document("$project", new Document("capacitacionId", 1)
                .append("titulo", 1).append("tipo", 1).append("modulo", 1).append("fechaInicio", 1)
                .append("estado", 1).append("cupo", 1).append("cuposOcupados", 1)
                .append("realizada", 1).append("inscritos", 1).append("asistieron", 1).append("marcadas", 1)
                .append("porcentajeAsistencia", new Document("$cond", Arrays.asList(
                        new Document("$gt", List.of("$marcadas", 0)),
                        redondear(new Document("$multiply", List.of(
                                new Document("$divide", List.of("$asistieron", "$marcadas")), 100)), 1),
                        null)))));
        return pipeline;
    }

    // ------------------------------------------------------------------
    // A7 · Artículos y tickets resueltos por módulo (articulos)
    // ------------------------------------------------------------------

    /**
     * A7, exactamente las etapas del documento de requisitos ({@code $lookup}, {@code $group}, {@code $project});
     * el orden por módulo lo pone el servicio (decidido en 1.6).
     *
     * <pre>
     * db.articulos.aggregate([
     *   { $lookup: { from: "tickets", localField: "ticketIds", foreignField: "_id",
     *                pipeline: [{ $project: { estado: 1 } }], as: "vinculados" } },
     *   { $group: { _id: "$modulo", articulos, publicos, conVector, idsVinculados: { $push }, idsResueltos: { $push } } },
     *   { $project: { _id: 0, modulo: "$_id", ..., ticketsVinculados, ticketsResueltos, resueltosPorArticulo } }
     * ])
     * </pre>
     */
    public static List<Document> a7ArticulosPorModulo() {
        return List.of(
                new Document("$lookup", new Document("from", "tickets")
                        .append("localField", "ticketIds")
                        .append("foreignField", "_id")
                        .append("pipeline", List.of(new Document("$project", new Document("estado", 1))))
                        .append("as", "vinculados")),
                new Document("$group", new Document("_id", "$modulo")
                        .append("articulos", new Document("$sum", 1))
                        .append("publicos", contarSi(new Document("$eq", List.of("$visibilidad", "PUBLICO"))))
                        .append("conVector", contarSi(new Document("$ne", Arrays.asList("$embedding", null))))
                        .append("idsVinculados", new Document("$push", "$vinculados._id"))
                        .append("idsResueltos", new Document("$push", new Document("$map",
                                new Document("input", new Document("$filter", new Document("input", "$vinculados")
                                        .append("cond", new Document("$in", List.of("$$this.estado", CERRADOS)))))
                                        .append("in", "$$this._id"))))),
                new Document("$project", new Document("_id", 0)
                        .append("modulo", "$_id")
                        .append("articulos", 1)
                        .append("publicos", 1)
                        .append("internos", new Document("$subtract", List.of("$articulos", "$publicos")))
                        .append("conVector", 1)
                        .append("ticketsVinculados", new Document("$size", unir("$idsVinculados")))
                        .append("ticketsResueltos", new Document("$size", unir("$idsResueltos")))
                        .append("resueltosPorArticulo", redondear(new Document("$divide", List.of(
                                new Document("$size", unir("$idsResueltos")), "$articulos")), 1))));
    }

    // ------------------------------------------------------------------
    // A8 · Empresas suspendidas con tickets abiertos (membresias)
    // ------------------------------------------------------------------

    /**
     * A8 con las etapas del documento ({@code $match}, {@code $lookup}, {@code $project}). Por decisión de
     * 2.9 la fila trae el conteo y la fecha del ticket abierto más antiguo, no la lista: el detalle se ve en
     * {@code GET /api/tickets?empresaId=}. El orden lo pone el servicio (1.6).
     *
     * <pre>
     * db.membresias.aggregate([
     *   { $match: { estado: "SUSPENDIDA" } },
     *   { $lookup: { from: "empresas", ... as: "empresa" } },
     *   { $lookup: { from: "planes", ... as: "plan" } },
     *   { $lookup: { from: "tickets", localField: "empresaId", foreignField: "empresaId", pipeline: [
     *       { $match: { estado: { $in: ["ABIERTO", "EN_PROCESO"] } } },
     *       { $project: { _id: 0, fechaCreacion: 1 } } ], as: "abiertos" } },
     *   { $project: { _id: 0, empresaId: 1, empresa, nit, contacto, telefono, email, plan,
     *                 suspendidaDesde: "$fechaActualizacion", ticketsAbiertos: { $size: "$abiertos" },
     *                 abiertoMasAntiguo: { $min: "$abiertos.fechaCreacion" } } },
     *   { $match: { ticketsAbiertos: { $gt: 0 } } }
     * ])
     * </pre>
     */
    public static List<Document> a8SuspendidasConAbiertos() {
        return List.of(
                new Document("$match", new Document("estado", "SUSPENDIDA")),
                new Document("$lookup", new Document("from", "empresas")
                        .append("localField", "empresaId")
                        .append("foreignField", "_id")
                        .append("pipeline", List.of(new Document("$project", new Document("_id", 0)
                                .append("nit", 1).append("razonSocial", 1).append("contacto", 1))))
                        .append("as", "empresa")),
                new Document("$lookup", new Document("from", "planes")
                        .append("localField", "planId")
                        .append("foreignField", "_id")
                        .append("pipeline", List.of(new Document("$project", new Document("_id", 0).append("nombre", 1))))
                        .append("as", "plan")),
                new Document("$lookup", new Document("from", "tickets")
                        .append("localField", "empresaId")
                        .append("foreignField", "empresaId")
                        .append("pipeline", List.of(
                                new Document("$match", new Document("estado", new Document("$in", ABIERTOS))),
                                new Document("$project", new Document("_id", 0).append("fechaCreacion", 1))))
                        .append("as", "abiertos")),
                new Document("$project", new Document("_id", 0)
                        .append("empresaId", 1)
                        .append("empresa", new Document("$first", "$empresa.razonSocial"))
                        .append("nit", new Document("$first", "$empresa.nit"))
                        .append("contacto", new Document("$first", "$empresa.contacto.nombre"))
                        .append("telefono", new Document("$first", "$empresa.contacto.telefono"))
                        .append("email", new Document("$first", "$empresa.contacto.email"))
                        .append("plan", new Document("$first", "$plan.nombre"))
                        .append("suspendidaDesde", "$fechaActualizacion")
                        .append("ticketsAbiertos", new Document("$size", "$abiertos"))
                        .append("abiertoMasAntiguo", new Document("$min", "$abiertos.fechaCreacion"))),
                new Document("$match", new Document("ticketsAbiertos", new Document("$gt", 0))));
    }

    // ------------------------------------------------------------------
    // A9 · Incidentes antes y después de la capacitación (inscripciones)
    // ------------------------------------------------------------------

    /**
     * A9, igual que el script, con la ventana como parámetro ({@code dias}, decidido en 2.9). Solo entran las
     * sesiones cuyas dos ventanas caben entre el primer y el último ticket (1.6): {@code finMinimo} y
     * {@code finMaximo} los calcula el servicio.
     *
     * <pre>
     * db.inscripciones.aggregate([
     *   { $match: { asistio: true } },
     *   { $lookup: { from: "capacitaciones", ..., pipeline: [
     *       { $match: { estado: "PROGRAMADA", fechaFin: { $gte: finMinimo, $lte: finMaximo } } },
     *       { $project: { _id: 0, modulo: 1, fechaFin: 1 } } ], as: "cap" } },
     *   { $match: { "cap.0": { $exists: true } } },
     *   { $lookup: { from: "tickets", let: { usuario, modulo, fin }, pipeline: [
     *       { $match: { $expr: { solicitanteId = usuario, modulo = modulo, fin - ventana <= fechaCreacion < fin + ventana },
     *                   tipo: "INCIDENTE" } },
     *       { $project: { _id: 0, fechaCreacion: 1 } } ], as: "incidentes" } },
     *   { $project: { _id: 0, modulo, antes, despues } },
     *   { $group: { _id: "$modulo", asistencias: { $sum: 1 }, antes: { $sum: "$antes" }, despues: { $sum: "$despues" } } },
     *   { $project: { _id: 0, modulo: "$_id", asistencias: 1, antes: 1, despues: 1, variacionPorcentual } },
     *   { $sort: { variacionPorcentual: 1, modulo: 1 } }
     * ])
     * </pre>
     */
    public static List<Document> a9ImpactoCapacitaciones(int dias, Instant finMinimo, Instant finMaximo) {
        long ventana = dias * MS_POR_DIA;
        Document fin = new Document("$first", "$cap.fechaFin");
        return List.of(
                new Document("$match", new Document("asistio", true)),
                new Document("$lookup", new Document("from", "capacitaciones")
                        .append("localField", "capacitacionId")
                        .append("foreignField", "_id")
                        .append("pipeline", List.of(
                                new Document("$match", new Document("estado", "PROGRAMADA")
                                        .append("fechaFin", new Document("$gte", Date.from(finMinimo))
                                                .append("$lte", Date.from(finMaximo)))),
                                new Document("$project", new Document("_id", 0).append("modulo", 1).append("fechaFin", 1))))
                        .append("as", "cap")),
                new Document("$match", new Document("cap.0", new Document("$exists", true))),
                new Document("$lookup", new Document("from", "tickets")
                        .append("let", new Document("usuario", "$usuarioId")
                                .append("modulo", new Document("$first", "$cap.modulo"))
                                .append("fin", fin))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$and", List.of(
                                        new Document("$eq", List.of("$solicitanteId", "$$usuario")),
                                        new Document("$eq", List.of("$modulo", "$$modulo")),
                                        new Document("$gte", List.of("$fechaCreacion",
                                                new Document("$subtract", List.of("$$fin", ventana)))),
                                        new Document("$lt", List.of("$fechaCreacion",
                                                new Document("$add", List.of("$$fin", ventana)))))))
                                        .append("tipo", "INCIDENTE")),
                                new Document("$project", new Document("_id", 0).append("fechaCreacion", 1))))
                        .append("as", "incidentes")),
                new Document("$project", new Document("_id", 0)
                        .append("modulo", new Document("$first", "$cap.modulo"))
                        .append("antes", new Document("$size", new Document("$filter", new Document("input", "$incidentes")
                                .append("cond", new Document("$lt", List.of("$$this.fechaCreacion", fin))))))
                        .append("despues", new Document("$size", new Document("$filter", new Document("input", "$incidentes")
                                .append("cond", new Document("$gte", List.of("$$this.fechaCreacion", fin))))))),
                new Document("$group", new Document("_id", "$modulo")
                        .append("asistencias", new Document("$sum", 1))
                        .append("antes", new Document("$sum", "$antes"))
                        .append("despues", new Document("$sum", "$despues"))),
                new Document("$project", new Document("_id", 0)
                        .append("modulo", "$_id")
                        .append("asistencias", 1)
                        .append("antes", 1)
                        .append("despues", 1)
                        .append("variacionPorcentual", new Document("$cond", Arrays.asList(
                                new Document("$gt", List.of("$antes", 0)),
                                redondear(new Document("$multiply", List.of(new Document("$divide", List.of(
                                        new Document("$subtract", List.of("$despues", "$antes")), "$antes")), 100)), 1),
                                null)))),
                new Document("$sort", new Document("variacionPorcentual", 1).append("modulo", 1)));
    }

    // ------------------------------------------------------------------
    // Piezas repetidas
    // ------------------------------------------------------------------

    /** {@code { $sum: { $cond: [condicion, 1, 0] } }} */
    private static Document contarSi(Document condicion) {
        return new Document("$sum", new Document("$cond", List.of(condicion, 1, 0)));
    }

    /** {@code { $round: [expresion, decimales] }} */
    private static Document redondear(Document expresion, int decimales) {
        return new Document("$round", List.of(expresion, decimales));
    }

    /** {@code { $ifNull: [{ $first: campo }, 0] }} */
    private static Document primeroOCero(String campo) {
        return new Document("$ifNull", List.of(new Document("$first", campo), 0));
    }

    /** Une arreglos de arreglos sin repetir (A7): {@code [[a, b], [b, c]] -> [a, b, c]}. */
    private static Document unir(String campo) {
        return new Document("$reduce", new Document("input", campo)
                .append("initialValue", List.of())
                .append("in", new Document("$setUnion", List.of("$$value", "$$this"))));
    }
}
