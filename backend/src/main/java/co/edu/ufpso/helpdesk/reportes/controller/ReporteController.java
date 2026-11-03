package co.edu.ufpso.helpdesk.reportes.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
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
import co.edu.ufpso.helpdesk.reportes.service.ReportesService;

/**
 * Reportes A1 a A9 (tarea 2.9), uno por endpoint. Todos exigen {@code REPORTES_VER}, que solo tienen roles
 * GLOBAL: no hay filtro por empresa ni bloqueo por soporte (matriz de permisos). Las fechas van como
 * {@code AAAA-MM-DD} y los meses como {@code AAAA-MM}, en hora de Bogotá.
 */
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAuthority('REPORTES_VER')")
public class ReporteController {

    private final ReportesService servicio;

    public ReporteController(ReportesService servicio) {
        this.servicio = servicio;
    }

    /** A1 · HU22: {@code ?desde=2026-01-01&hasta=2026-10-31}. Sin fechas, toda la historia. */
    @GetMapping("/incidentes-por-modulo")
    public Reporte<List<FilaIncidentesModulo>> incidentesPorModulo(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return servicio.incidentesPorModulo(desde, hasta);
    }

    /** A2 · HU10. */
    @GetMapping("/membresias-activas-por-plan")
    public Reporte<List<FilaMembresiasPlan>> membresiasActivasPorPlan() {
        return servicio.membresiasActivasPorPlan();
    }

    /** A3 · HU06. */
    @GetMapping("/carga-por-agente")
    public Reporte<List<FilaCargaAgente>> cargaPorAgente() {
        return servicio.cargaPorAgente();
    }

    /** A4 · HU02: {@code ?pagina=0&tamano=20}. Orden fijo. */
    @GetMapping("/plan-y-tickets-por-empresa")
    public Reporte<Pagina<FilaPlanTicketsEmpresa>> planYTicketsPorEmpresa(Pageable pageable) {
        return servicio.planYTicketsPorEmpresa(pageable);
    }

    /** A5 · HU18: {@code ?desde=2025-11&hasta=2026-10&empresaId=}. Por defecto, los últimos 12 meses. */
    @GetMapping("/recaudo-mensual")
    public Reporte<List<FilaRecaudoMes>> recaudoMensual(@RequestParam(required = false) YearMonth desde,
            @RequestParam(required = false) YearMonth hasta, @RequestParam(required = false) String empresaId) {
        return servicio.recaudoMensual(desde, hasta, empresaId);
    }

    /** A6 · HU34: {@code ?desde=&hasta=&modulo=&pagina=0&tamano=20}. Orden fijo: la más reciente primero. */
    @GetMapping("/asistencia-por-capacitacion")
    public Reporte<Pagina<FilaAsistenciaCapacitacion>> asistenciaPorCapacitacion(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) ModuloErp modulo, Pageable pageable) {
        return servicio.asistenciaPorCapacitacion(desde, hasta, modulo, pageable);
    }

    /** A7 · HU30. */
    @GetMapping("/articulos-por-modulo")
    public Reporte<List<FilaArticulosModulo>> articulosPorModulo() {
        return servicio.articulosPorModulo();
    }

    /** A8 · HU14. */
    @GetMapping("/suspendidas-con-tickets-abiertos")
    public Reporte<List<FilaSuspendidaConAbiertos>> suspendidasConTicketsAbiertos() {
        return servicio.suspendidasConTicketsAbiertos();
    }

    /** A9 (fuera del documento): {@code ?dias=90}, entre 30 y 180. */
    @GetMapping("/impacto-capacitaciones")
    public Reporte<List<FilaImpactoCapacitacion>> impactoCapacitaciones(@RequestParam(required = false) Integer dias) {
        return servicio.impactoCapacitaciones(dias);
    }
}
