package co.edu.ufpso.helpdesk.reportes.service;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Ids;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.common.paginacion.Pagina;
import co.edu.ufpso.helpdesk.reportes.dto.FilaArticulosModulo;
import co.edu.ufpso.helpdesk.reportes.dto.FilaAsistenciaCapacitacion;
import co.edu.ufpso.helpdesk.reportes.dto.FilaCargaAgente;
import co.edu.ufpso.helpdesk.reportes.dto.FilaImpactoCapacitacion;
import co.edu.ufpso.helpdesk.reportes.dto.FilaIncidentesModulo;
import co.edu.ufpso.helpdesk.reportes.dto.FilaMembresiasPlan;
import co.edu.ufpso.helpdesk.reportes.dto.FilaPlanTicketsEmpresa;
import co.edu.ufpso.helpdesk.reportes.dto.FilaRecaudoMes;
import co.edu.ufpso.helpdesk.reportes.dto.FilaSuspendidaConAbiertos;
import co.edu.ufpso.helpdesk.reportes.dto.Reporte;

/**
 * Corre los reportes A1 a A9 (tarea 2.9) con los pipelines de {@link PipelinesReportes} y convierte cada
 * documento en su fila.
 *
 * <p>Lee las colecciones por nombre con el driver ({@link MongoTemplate#getDb()}), igual que los scripts de
 * {@code database/reportes/}. No importa el {@code domain} ni el {@code repository} de otro módulo: un reporte
 * cruza varias colecciones y no es dueño de ninguna (regla de 2.2; la prueba de 2.10 debe permitirlo).
 *
 * <p>{@code REPORTES_VER} solo lo tienen roles GLOBAL, así que ningún reporte filtra por empresa ni depende
 * del soporte (matriz de permisos). La hora actual sale del bean {@link Clock}. Los rangos de días se cuentan
 * en hora de Bogotá con los dos extremos inclusivos, como los filtros de tickets (2.6).
 */
@Service
public class ReportesService {

    public static final ZoneId BOGOTA = ZoneId.of(PipelinesReportes.ZONA);
    public static final int DIAS_POR_DEFECTO = 90;
    public static final int DIAS_MINIMO = 30;
    public static final int DIAS_MAXIMO = 180;
    public static final int MESES_POR_DEFECTO = 12;
    public static final int MESES_MAXIMO = 24;

    private final MongoTemplate template;
    private final Clock reloj;

    public ReportesService(MongoTemplate template, Clock reloj) {
        this.template = template;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // A1
    // ------------------------------------------------------------------

    /** A1 · HU22. Sin fechas, toda la historia. */
    public Reporte<List<FilaIncidentesModulo>> incidentesPorModulo(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        List<FilaIncidentesModulo> filas = ejecutar("tickets",
                PipelinesReportes.a1IncidentesPorModulo(inicioDe(desde), finDe(hasta)),
                d -> new FilaIncidentesModulo(d.getString("modulo"), entero(d, "incidentes"), entero(d, "abiertos"),
                        entero(d, "cerrados"), doble(d, "horasPromedioResolucion"), doble(d, "porcentajeCerrados")));
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("desde", desde);
        parametros.put("hasta", hasta);
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("totalIncidentes", filas.stream().mapToInt(FilaIncidentesModulo::incidentes).sum());
        return new Reporte<>("A1", "Incidentes y tiempo promedio de resolución por módulo", ahora(), parametros,
                resumen, filas);
    }

    // ------------------------------------------------------------------
    // A2
    // ------------------------------------------------------------------

    /** A2 · HU10. Todos los planes, también los inactivos con 0 empresas. */
    public Reporte<List<FilaMembresiasPlan>> membresiasActivasPorPlan() {
        Instant ahora = ahora();
        List<FilaMembresiasPlan> filas = ejecutar("planes", PipelinesReportes.a2MembresiasActivasPorPlan(ahora),
                d -> new FilaMembresiasPlan(id(d, "planId"), d.getString("plan"), d.getString("nivelAcceso"),
                        dinero(d, "precio"), Boolean.TRUE.equals(d.getBoolean("activo")), entero(d, "empresasActivas"),
                        entero(d, "vigentes"), entero(d, "vencidas"), d.getList("empresas", String.class, List.of())));
        return new Reporte<>("A2", "Empresas con membresía activa por plan", ahora, null, null, filas);
    }

    // ------------------------------------------------------------------
    // A3
    // ------------------------------------------------------------------

    /**
     * A3 · HU06. "Agente" = usuario con un rol GLOBAL activo que tenga {@code TICKETS_ASIGNAR} (1.6). El resumen
     * suma los abiertos asignados y cuenta aparte los abiertos sin agente.
     */
    public Reporte<List<FilaCargaAgente>> cargaPorAgente() {
        List<ObjectId> rolesAgente = new ArrayList<>();
        coleccion("roles")
                .find(and(eq("permisos", Permiso.TICKETS_ASIGNAR.name()), eq("alcance", AlcanceRol.GLOBAL.name()),
                        eq("activo", true)))
                .projection(include("_id"))
                .forEach(r -> rolesAgente.add(r.getObjectId("_id")));

        List<FilaCargaAgente> filas = ejecutar("usuarios", PipelinesReportes.a3CargaPorAgente(rolesAgente),
                d -> new FilaCargaAgente(id(d, "usuarioId"), d.getString("nombre"), d.getString("email"),
                        Boolean.TRUE.equals(d.getBoolean("activo")), entero(d, "abiertos"), entero(d, "enProceso"),
                        fecha(d, "masAntiguo")));
        // ix_tickets_agente_estado_fecha: agenteId null y estado con dos valores.
        long sinAsignar = coleccion("tickets").countDocuments(
                and(eq("agenteId", null), in("estado", PipelinesReportes.ABIERTOS)));
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("agentes", filas.size());
        resumen.put("abiertosAsignados", filas.stream().mapToInt(FilaCargaAgente::abiertos).sum());
        resumen.put("abiertosSinAsignar", sinAsignar);
        return new Reporte<>("A3", "Carga de tickets abiertos por agente", ahora(), null, resumen, filas);
    }

    // ------------------------------------------------------------------
    // A4
    // ------------------------------------------------------------------

    /** A4 · HU02. Paginado; el orden es fijo (más tickets primero). */
    public Reporte<Pagina<FilaPlanTicketsEmpresa>> planYTicketsPorEmpresa(Pageable pageable) {
        sinOrden(pageable);
        Instant ahora = ahora();
        List<FilaPlanTicketsEmpresa> filas = ejecutar("empresas",
                PipelinesReportes.a4PlanYTicketsPorEmpresa(ahora, pageable.getOffset(), pageable.getPageSize()),
                d -> new FilaPlanTicketsEmpresa(id(d, "empresaId"), d.getString("nit"), d.getString("razonSocial"),
                        d.getString("plan"), d.getString("estadoMembresia"), entero(d, "modulos"), entero(d, "tickets"),
                        entero(d, "abiertos"), entero(d, "incidentes"), entero(d, "sugerencias")));
        long total = coleccion("empresas").countDocuments();
        return new Reporte<>("A4", "Plan contratado y tickets por empresa", ahora, null, null,
                pagina(filas, pageable, total));
    }

    // ------------------------------------------------------------------
    // A5
    // ------------------------------------------------------------------

    /**
     * A5 · HU18. Meses de Bogotá. Sin {@code desde} ni {@code hasta}: los últimos {@value #MESES_POR_DEFECTO}
     * meses con el actual. Como máximo {@value #MESES_MAXIMO} meses.
     */
    public Reporte<List<FilaRecaudoMes>> recaudoMensual(YearMonth desde, YearMonth hasta, String empresaId) {
        ObjectId empresa = Ids.deParametro("empresaId", empresaId);
        YearMonth fin = hasta != null ? hasta
                : desde != null ? desde.plusMonths(MESES_POR_DEFECTO - 1L) : YearMonth.now(reloj.withZone(BOGOTA));
        YearMonth inicio = desde != null ? desde : fin.minusMonths(MESES_POR_DEFECTO - 1L);
        if (inicio.isAfter(fin)) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO, "El mes 'desde' no puede ser posterior a 'hasta'.");
        }
        if (ChronoUnit.MONTHS.between(inicio, fin) + 1 > MESES_MAXIMO) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                    "El periodo admite como máximo " + MESES_MAXIMO + " meses.");
        }
        Instant desdeInstante = inicio.atDay(1).atStartOfDay(BOGOTA).toInstant();
        Instant hastaInstante = fin.plusMonths(1).atDay(1).atStartOfDay(BOGOTA).toInstant();

        List<FilaRecaudoMes> filas = ejecutar("pagos",
                PipelinesReportes.a5RecaudoMensual(PipelinesReportes.a5Filtro(empresa, desdeInstante, hastaInstante)),
                d -> {
                    int aprobados = entero(d, "aprobados");
                    int fallidos = entero(d, "fallidos");
                    return new FilaRecaudoMes(d.getString("_id"), dinero(d, "recaudo"), aprobados, fallidos,
                            dinero(d, "montoFallido"), porcentaje(fallidos, aprobados + fallidos));
                });
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("desde", inicio);
        parametros.put("hasta", fin);
        parametros.put("empresaId", empresa == null ? null : empresa.toHexString());
        int aprobados = filas.stream().mapToInt(FilaRecaudoMes::aprobados).sum();
        int fallidos = filas.stream().mapToInt(FilaRecaudoMes::fallidos).sum();
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("recaudoTotal", filas.stream().map(FilaRecaudoMes::recaudo).reduce(BigDecimal.ZERO, BigDecimal::add));
        resumen.put("pagosAprobados", aprobados);
        resumen.put("pagosFallidos", fallidos);
        resumen.put("porcentajeFallidos", porcentaje(fallidos, aprobados + fallidos));
        return new Reporte<>("A5", "Recaudo y pagos fallidos por mes", ahora(), parametros, resumen, filas);
    }

    // ------------------------------------------------------------------
    // A6
    // ------------------------------------------------------------------

    /** A6 · HU34. Paginado, de la más reciente a la más antigua; filtros opcionales por fecha de inicio y módulo. */
    public Reporte<Pagina<FilaAsistenciaCapacitacion>> asistenciaPorCapacitacion(LocalDate desde, LocalDate hasta,
            ModuloErp modulo, Pageable pageable) {
        sinOrden(pageable);
        validarRango(desde, hasta);
        Instant ahora = ahora();
        Document filtro = PipelinesReportes.a6Filtro(inicioDe(desde), finDe(hasta), modulo);
        List<FilaAsistenciaCapacitacion> filas = ejecutar("capacitaciones",
                PipelinesReportes.a6AsistenciaPorCapacitacion(filtro, ahora, pageable.getOffset(), pageable.getPageSize()),
                d -> new FilaAsistenciaCapacitacion(id(d, "capacitacionId"), d.getString("titulo"), d.getString("tipo"),
                        d.getString("modulo"), fecha(d, "fechaInicio"), d.getString("estado"),
                        Boolean.TRUE.equals(d.getBoolean("realizada")), entero(d, "cupo"), entero(d, "cuposOcupados"),
                        entero(d, "inscritos"), entero(d, "asistieron"), entero(d, "marcadas"),
                        doble(d, "porcentajeAsistencia")));
        long total = coleccion("capacitaciones").countDocuments(filtro);
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("desde", desde);
        parametros.put("hasta", hasta);
        parametros.put("modulo", modulo);
        return new Reporte<>("A6", "Porcentaje de asistencia por capacitación", ahora, parametros, null,
                pagina(filas, pageable, total));
    }

    // ------------------------------------------------------------------
    // A7
    // ------------------------------------------------------------------

    /** A7 · HU30. Ordenado por módulo aquí, no en el pipeline (1.6). */
    public Reporte<List<FilaArticulosModulo>> articulosPorModulo() {
        List<FilaArticulosModulo> filas = new ArrayList<>(ejecutar("articulos", PipelinesReportes.a7ArticulosPorModulo(),
                d -> new FilaArticulosModulo(d.getString("modulo"), entero(d, "articulos"), entero(d, "publicos"),
                        entero(d, "internos"), entero(d, "conVector"), entero(d, "ticketsVinculados"),
                        entero(d, "ticketsResueltos"), dobleOCero(d, "resueltosPorArticulo"))));
        filas.sort(Comparator.comparing(FilaArticulosModulo::modulo));
        return new Reporte<>("A7", "Artículos y tickets resueltos por módulo", ahora(), null, null, filas);
    }

    // ------------------------------------------------------------------
    // A8
    // ------------------------------------------------------------------

    /** A8 · HU14. Ordenado aquí (1.6): más tickets abiertos primero y, a igual cantidad, por nombre. */
    public Reporte<List<FilaSuspendidaConAbiertos>> suspendidasConTicketsAbiertos() {
        List<FilaSuspendidaConAbiertos> filas = new ArrayList<>(ejecutar("membresias",
                PipelinesReportes.a8SuspendidasConAbiertos(),
                d -> new FilaSuspendidaConAbiertos(id(d, "empresaId"), d.getString("empresa"), d.getString("nit"),
                        d.getString("contacto"), d.getString("telefono"), d.getString("email"), d.getString("plan"),
                        fecha(d, "suspendidaDesde"), entero(d, "ticketsAbiertos"), fecha(d, "abiertoMasAntiguo"))));
        filas.sort(Comparator.comparingInt(FilaSuspendidaConAbiertos::ticketsAbiertos).reversed()
                .thenComparing(FilaSuspendidaConAbiertos::empresa, Comparator.nullsLast(Comparator.naturalOrder())));
        return new Reporte<>("A8", "Empresas suspendidas con tickets abiertos", ahora(), null, null, filas);
    }

    // ------------------------------------------------------------------
    // A9
    // ------------------------------------------------------------------

    /**
     * A9 (fuera del documento de requisitos). Ventana de {@code dias} a cada lado de {@code fechaFin}: por
     * defecto {@value #DIAS_POR_DEFECTO}, entre {@value #DIAS_MINIMO} y {@value #DIAS_MAXIMO}. El resumen dice
     * entre qué fechas terminaron las sesiones que entraron.
     */
    public Reporte<List<FilaImpactoCapacitacion>> impactoCapacitaciones(Integer dias) {
        int ventana = dias == null ? DIAS_POR_DEFECTO : dias;
        if (ventana < DIAS_MINIMO || ventana > DIAS_MAXIMO) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                    "El parámetro 'dias' debe estar entre " + DIAS_MINIMO + " y " + DIAS_MAXIMO + ".");
        }
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("dias", ventana);
        Map<String, Object> resumen = new LinkedHashMap<>();

        // Primer y último ticket por _id (índice _id_): el ObjectId empieza por la fecha de inserción.
        MongoCollection<Document> tickets = coleccion("tickets");
        Document primero = tickets.find().projection(include("fechaCreacion")).sort(ascending("_id")).first();
        Document ultimo = tickets.find().projection(include("fechaCreacion")).sort(descending("_id")).first();
        List<FilaImpactoCapacitacion> filas = List.of();
        if (primero != null && ultimo != null) {
            Instant finMinimo = fecha(primero, "fechaCreacion").plus(ventana, ChronoUnit.DAYS);
            Instant finMaximo = fecha(ultimo, "fechaCreacion").minus(ventana, ChronoUnit.DAYS);
            resumen.put("fechaFinDesde", finMinimo);
            resumen.put("fechaFinHasta", finMaximo);
            if (!finMinimo.isAfter(finMaximo)) {
                filas = ejecutar("inscripciones", PipelinesReportes.a9ImpactoCapacitaciones(ventana, finMinimo, finMaximo),
                        d -> new FilaImpactoCapacitacion(d.getString("modulo"), entero(d, "asistencias"),
                                entero(d, "antes"), entero(d, "despues"), doble(d, "variacionPorcentual")));
            }
        } else {
            resumen.put("fechaFinDesde", null);
            resumen.put("fechaFinHasta", null);
        }
        resumen.put("asistencias", filas.stream().mapToInt(FilaImpactoCapacitacion::asistencias).sum());
        return new Reporte<>("A9", "Incidentes antes y después de la capacitación", ahora(), parametros, resumen, filas);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private MongoCollection<Document> coleccion(String nombre) {
        return template.getDb().getCollection(nombre);
    }

    private <T> List<T> ejecutar(String coleccion, List<Document> pipeline, Function<Document, T> fila) {
        List<T> filas = new ArrayList<>();
        for (Document d : coleccion(coleccion).aggregate(pipeline)) {
            filas.add(fila.apply(d));
        }
        return filas;
    }

    private Instant ahora() {
        return Instant.now(reloj).truncatedTo(ChronoUnit.MILLIS);
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO, "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
    }

    /** Medianoche de Bogotá del día, o null. */
    private static Instant inicioDe(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(BOGOTA).toInstant();
    }

    /** Medianoche de Bogotá del día siguiente (extremo exclusivo), o null. */
    private static Instant finDe(LocalDate dia) {
        return dia == null ? null : dia.plusDays(1).atStartOfDay(BOGOTA).toInstant();
    }

    /** A4 y A6 tienen un orden fijo; un {@code orden} en la URL se rechaza en lugar de ignorarlo. */
    private static void sinOrden(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            throw new ApiException(CodigoError.PARAMETRO_INVALIDO,
                    "Este reporte tiene un orden fijo: no admite el parámetro 'orden'.");
        }
    }

    private static <T> Pagina<T> pagina(List<T> filas, Pageable pageable, long total) {
        int tamano = pageable.getPageSize();
        int paginas = (int) ((total + tamano - 1) / tamano);
        return new Pagina<>(filas, pageable.getPageNumber(), tamano, total, paginas);
    }

    private static double porcentaje(int parte, int total) {
        return total == 0 ? 0.0 : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private static int entero(Document d, String campo) {
        Object v = d.get(campo);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static Double doble(Document d, String campo) {
        Object v = d.get(campo);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static double dobleOCero(Document d, String campo) {
        Double v = doble(d, campo);
        return v == null ? 0.0 : v;
    }

    private static BigDecimal dinero(Document d, String campo) {
        Object v = d.get(campo);
        if (v instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }

    private static Instant fecha(Document d, String campo) {
        Object v = d.get(campo);
        return v instanceof Date f ? f.toInstant() : null;
    }

    private static String id(Document d, String campo) {
        Object v = d.get(campo);
        return v instanceof ObjectId o ? o.toHexString() : null;
    }
}
