package co.edu.ufpso.helpdesk.reportes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.MongoClient;

import co.edu.ufpso.helpdesk.capacitaciones.domain.Capacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.EstadoCapacitacion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.Inscripcion;
import co.edu.ufpso.helpdesk.capacitaciones.domain.TipoCapacitacion;
import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;
import co.edu.ufpso.helpdesk.conocimiento.domain.Visibilidad;
import co.edu.ufpso.helpdesk.conocimiento.service.GeminiEmbeddings;
import co.edu.ufpso.helpdesk.empresas.domain.Contacto;
import co.edu.ufpso.helpdesk.empresas.domain.Empresa;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.membresias.domain.MetodoPago;
import co.edu.ufpso.helpdesk.membresias.domain.Pago;
import co.edu.ufpso.helpdesk.membresias.domain.ResultadoPago;
import co.edu.ufpso.helpdesk.planes.domain.LimitesPlan;
import co.edu.ufpso.helpdesk.planes.domain.NivelAcceso;
import co.edu.ufpso.helpdesk.planes.domain.Plan;
import co.edu.ufpso.helpdesk.roles.domain.Rol;
import co.edu.ufpso.helpdesk.soporte.ProveedorEmbeddingsFalso;
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.tickets.domain.CambioEstado;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleInventarios;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleTicket;
import co.edu.ufpso.helpdesk.tickets.domain.DetalleVentasOnline;
import co.edu.ufpso.helpdesk.tickets.domain.EstadoTicket;
import co.edu.ufpso.helpdesk.tickets.domain.Ticket;
import co.edu.ufpso.helpdesk.tickets.domain.TipoTicket;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Reportes A1 a A9 (tarea 2.9) de punta a punta: token real, validadores reales del {@code rs0} local copiados
 * a {@code helpdesk_test} y un conjunto pequeño de datos cuyos resultados se calculan a mano.
 *
 * <p>Los tickets llevan un {@code _id} creado con su {@code fechaCreacion}, como en la semilla: A9 toma el
 * primer y el último ticket por {@code _id}.
 *
 * <p>Requisito en el local: {@code database/validadores/00_todos_los_validadores.js}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesIntegracionTest {

    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private JwtEncoder encoder;

    private Instant ahora;
    private Usuario admin;
    private Usuario agente;
    private Usuario sinReportes;
    private Usuario clienteX;
    private Usuario clienteY;
    private Usuario clienteZ;
    private Empresa alfa;
    private Empresa beta;
    private Ticket t1;
    private Ticket t2;
    private Ticket t4;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "planes", "membresias", "pagos", "tickets",
                "articulos", "capacitaciones", "inscripciones")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Roles: dos con TICKETS_ASIGNAR (agentes de A3), uno sin REPORTES_VER y uno de empresa.
        Rol rolAdmin = rol("ADMIN", AlcanceRol.GLOBAL, List.of(Permiso.REPORTES_VER, Permiso.TICKETS_ASIGNAR,
                Permiso.TICKETS_ASIGNAR_OTROS));
        Rol rolAgente = rol("AGENTE_SOPORTE", AlcanceRol.GLOBAL, List.of(Permiso.REPORTES_VER, Permiso.TICKETS_ASIGNAR));
        Rol rolSinReportes = rol("SOLO_TICKETS", AlcanceRol.GLOBAL, List.of(Permiso.TICKETS_VER));
        Rol rolCliente = rol("CLIENTE", AlcanceRol.EMPRESA, List.of(Permiso.TICKETS_CREAR, Permiso.INSCRIPCIONES_PROPIAS));

        // Empresas: Alfa (ACTIVA vigente), Beta (SUSPENDIDA), Gamma (ACTIVA vencida) y Delta (sin membresía).
        alfa = empresa("900111111-1", "Alfa Ltda");
        beta = empresa("900222222-2", "Beta SAS");
        Empresa gamma = empresa("900333333-3", "Gamma y Cía");
        empresa("900444444-4", "Delta SA");

        Plan basico = plan("Básico", NivelAcceso.BASICO, "100000", true);
        Plan premium = plan("Premium", NivelAcceso.PREMIUM, "300000", true);
        plan("Antiguo", NivelAcceso.ESTANDAR, "50000", false);
        Membresia mAlfa = membresia(alfa, basico, EstadoMembresia.ACTIVA, ahora.plus(Duration.ofDays(30)),
                List.of(ModuloErp.VENTAS_ONLINE, ModuloErp.INVENTARIOS));
        Membresia mBeta = membresia(beta, premium, EstadoMembresia.SUSPENDIDA, ahora.plus(Duration.ofDays(10)),
                List.of(ModuloErp.INVENTARIOS));
        membresia(gamma, basico, EstadoMembresia.ACTIVA, ahora.minus(Duration.ofDays(1)), List.of(ModuloErp.COMPRAS));

        admin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, rolAdmin);
        agente = usuario("Andrés Pabón", "andres@sistemasintegrados.com.co", null, rolAgente);
        sinReportes = usuario("Rosa Vera", "rosa@sistemasintegrados.com.co", null, rolSinReportes);
        clienteX = usuario("Camilo Quintero", "camilo@alfa.co", alfa.getId(), rolCliente);
        clienteY = usuario("Diana Ruiz", "diana@beta.co", beta.getId(), rolCliente);
        clienteZ = usuario("Ernesto Gil", "ernesto@alfa.co", alfa.getId(), rolCliente);

        // Tickets.
        t1 = ticket(clienteX, ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, EstadoTicket.ABIERTO, agente,
                hace(10), null);
        t2 = ticket(clienteX, ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, EstadoTicket.RESUELTO, agente,
                hace(20), Duration.ofHours(10));
        t4 = ticket(clienteY, ModuloErp.INVENTARIOS, TipoTicket.INCIDENTE, EstadoTicket.EN_PROCESO, agente,
                hace(5), null);
        ticket(clienteY, ModuloErp.INVENTARIOS, TipoTicket.SUGERENCIA, EstadoTicket.ABIERTO, null, hace(2), null);

        // Pagos: dos meses atrás y el mes pasado, en hora de Bogotá.
        YearMonth mes = YearMonth.now(BOGOTA);
        pago(mAlfa, "100000", ResultadoPago.APROBADO, mitadDe(mes.minusMonths(2)));
        pago(mAlfa, "100000", ResultadoPago.APROBADO, mitadDe(mes.minusMonths(1)));
        pago(mBeta, "150000", ResultadoPago.FALLIDO, mitadDe(mes.minusMonths(1)));

        // Capacitaciones: una masiva realizada con tres inscritos y una personalizada futura sin inscritos.
        Capacitacion masiva = capacitacion("Pasarelas de pago", TipoCapacitacion.MASIVA, ModuloErp.VENTAS_ONLINE, null,
                hace(10), 3);
        inscripcion(masiva, clienteX, true);
        inscripcion(masiva, clienteY, false);
        inscripcion(masiva, clienteZ, null);
        capacitacion("Inventario de Beta", TipoCapacitacion.PERSONALIZADA, ModuloErp.INVENTARIOS, beta.getId(),
                ahora.plus(Duration.ofDays(5)), 0);

        // Artículos: dos de Ventas Online (uno con vector) y uno de Inventarios sin tickets.
        articulo("Pago rechazado", ModuloErp.VENTAS_ONLINE, Visibilidad.PUBLICO, List.of(t1.getId(), t2.getId()), false);
        articulo("Reintentos de la pasarela", ModuloErp.VENTAS_ONLINE, Visibilidad.INTERNO, List.of(t2.getId()), true);
        articulo("Alertas de stock", ModuloErp.INVENTARIOS, Visibilidad.PUBLICO, List.of(), false);
    }

    // ---------- permisos ----------

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/reportes/incidentes-por-modulo")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinReportesVerResponde403EnLosNueve() throws Exception {
        for (String ruta : List.of("incidentes-por-modulo", "membresias-activas-por-plan", "carga-por-agente",
                "plan-y-tickets-por-empresa", "recaudo-mensual", "asistencia-por-capacitacion", "articulos-por-modulo",
                "suspendidas-con-tickets-abiertos", "impacto-capacitaciones")) {
            mvc.perform(como(sinReportes, get("/api/reportes/" + ruta)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        }
    }

    // ---------- A1 ----------

    @Test
    void a1CuentaIncidentesPorModuloSinSugerencias() throws Exception {
        mvc.perform(como(agente, get("/api/reportes/incidentes-por-modulo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporte").value("A1"))
                .andExpect(jsonPath("$.resumen.totalIncidentes").value(3))
                .andExpect(jsonPath("$.filas[*].modulo", contains("VENTAS_ONLINE", "INVENTARIOS")))
                .andExpect(jsonPath("$.filas[0].incidentes").value(2))
                .andExpect(jsonPath("$.filas[0].abiertos").value(1))
                .andExpect(jsonPath("$.filas[0].cerrados").value(1))
                .andExpect(jsonPath("$.filas[0].horasPromedioResolucion").value(10.0))
                .andExpect(jsonPath("$.filas[0].porcentajeCerrados").value(50.0))
                .andExpect(jsonPath("$.filas[1].incidentes").value(1))
                .andExpect(jsonPath("$.filas[1].horasPromedioResolucion").value(nullValue()));
    }

    @Test
    void a1FiltraPorDiasDeBogota() throws Exception {
        String desde = LocalDate.now(BOGOTA).minusDays(7).toString();
        mvc.perform(como(admin, get("/api/reportes/incidentes-por-modulo").param("desde", desde)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.desde").value(desde))
                .andExpect(jsonPath("$.filas[*].modulo", contains("INVENTARIOS")));

        mvc.perform(como(admin, get("/api/reportes/incidentes-por-modulo")
                .param("desde", "2026-10-31").param("hasta", "2026-10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ---------- A2 ----------

    @Test
    void a2SeparaVigentesDeVencidasEIncluyeElPlanInactivo() throws Exception {
        mvc.perform(como(agente, get("/api/reportes/membresias-activas-por-plan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].plan", contains("Básico", "Antiguo", "Premium")))
                .andExpect(jsonPath("$.filas[0].empresasActivas").value(2))
                .andExpect(jsonPath("$.filas[0].vigentes").value(1))
                .andExpect(jsonPath("$.filas[0].vencidas").value(1))
                .andExpect(jsonPath("$.filas[0].empresas", contains("Alfa Ltda", "Gamma y Cía")))
                .andExpect(jsonPath("$.filas[1].activo").value(false))
                .andExpect(jsonPath("$.filas[2].empresasActivas").value(0));
    }

    // ---------- A3 ----------

    @Test
    void a3CuentaAbiertosPorAgenteYSinAsignar() throws Exception {
        mvc.perform(como(admin, get("/api/reportes/carga-por-agente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].nombre", contains("Andrés Pabón", "Liliana Contreras")))
                .andExpect(jsonPath("$.filas[0].usuarioId").value(agente.getId().toHexString()))
                .andExpect(jsonPath("$.filas[0].abiertos").value(2))
                .andExpect(jsonPath("$.filas[0].enProceso").value(1))
                .andExpect(jsonPath("$.filas[0].masAntiguo").value(t1.getFechaCreacion().toString()))
                .andExpect(jsonPath("$.filas[1].abiertos").value(0))
                .andExpect(jsonPath("$.filas[1].masAntiguo").value(nullValue()))
                .andExpect(jsonPath("$.resumen.abiertosAsignados").value(2))
                .andExpect(jsonPath("$.resumen.abiertosSinAsignar").value(1));
    }

    // ---------- A4 ----------

    @Test
    void a4PaginaEmpresasConPlanYTickets() throws Exception {
        mvc.perform(como(admin, get("/api/reportes/plan-y-tickets-por-empresa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas.totalElementos").value(4))
                .andExpect(jsonPath("$.filas.contenido[*].razonSocial",
                        contains("Alfa Ltda", "Beta SAS", "Delta SA", "Gamma y Cía")))
                .andExpect(jsonPath("$.filas.contenido[0].plan").value("Básico"))
                .andExpect(jsonPath("$.filas.contenido[0].estadoMembresia").value("ACTIVA"))
                .andExpect(jsonPath("$.filas.contenido[0].modulos").value(2))
                .andExpect(jsonPath("$.filas.contenido[0].tickets").value(2))
                .andExpect(jsonPath("$.filas.contenido[0].abiertos").value(1))
                .andExpect(jsonPath("$.filas.contenido[1].estadoMembresia").value("SUSPENDIDA"))
                .andExpect(jsonPath("$.filas.contenido[1].abiertos").value(2))
                .andExpect(jsonPath("$.filas.contenido[1].sugerencias").value(1))
                .andExpect(jsonPath("$.filas.contenido[2].plan").value("Sin membresía"))
                .andExpect(jsonPath("$.filas.contenido[2].estadoMembresia").value("-"))
                .andExpect(jsonPath("$.filas.contenido[3].estadoMembresia").value("VENCIDA"));

        mvc.perform(como(admin, get("/api/reportes/plan-y-tickets-por-empresa").param("pagina", "1").param("tamano", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas.pagina").value(1))
                .andExpect(jsonPath("$.filas.totalPaginas").value(2))
                .andExpect(jsonPath("$.filas.contenido[*].razonSocial", contains("Delta SA", "Gamma y Cía")));

        mvc.perform(como(admin, get("/api/reportes/plan-y-tickets-por-empresa").param("orden", "nit,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ---------- A5 ----------

    @Test
    void a5AgrupaPorMesDeBogotaConDecimal128() throws Exception {
        YearMonth mes = YearMonth.now(BOGOTA);
        MvcResult r = mvc.perform(como(admin, get("/api/reportes/recaudo-mensual")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.desde").value(mes.minusMonths(11).toString()))
                .andExpect(jsonPath("$.parametros.hasta").value(mes.toString()))
                .andExpect(jsonPath("$.filas[*].mes", contains(mes.minusMonths(2).toString(), mes.minusMonths(1).toString())))
                .andExpect(jsonPath("$.filas[1].aprobados").value(1))
                .andExpect(jsonPath("$.filas[1].fallidos").value(1))
                .andExpect(jsonPath("$.filas[1].porcentajeFallidos").value(50.0))
                .andExpect(jsonPath("$.resumen.pagosFallidos").value(1))
                .andExpect(jsonPath("$.resumen.porcentajeFallidos").value(33.3))
                .andReturn();
        assertThat(dinero(r, "$.filas[1].recaudo")).isEqualByComparingTo("100000");
        assertThat(dinero(r, "$.filas[1].montoFallido")).isEqualByComparingTo("150000");
        assertThat(dinero(r, "$.resumen.recaudoTotal")).isEqualByComparingTo("200000");

        MvcResult soloBeta = mvc.perform(como(admin, get("/api/reportes/recaudo-mensual")
                .param("empresaId", beta.getId().toHexString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].fallidos").value(1))
                .andReturn();
        assertThat(dinero(soloBeta, "$.filas[0].recaudo")).isEqualByComparingTo("0");
    }

    @Test
    void a5ValidaElPeriodo() throws Exception {
        mvc.perform(como(admin, get("/api/reportes/recaudo-mensual").param("desde", "2026-10").param("hasta", "2026-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(como(admin, get("/api/reportes/recaudo-mensual").param("desde", "2024-01").param("hasta", "2026-10")))
                .andExpect(status().isBadRequest());
        mvc.perform(como(admin, get("/api/reportes/recaudo-mensual").param("desde", "octubre")))
                .andExpect(status().isBadRequest());
        mvc.perform(como(admin, get("/api/reportes/recaudo-mensual").param("empresaId", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ---------- A6 ----------

    @Test
    void a6CalculaElPorcentajeSobreLasMarcadas() throws Exception {
        mvc.perform(como(agente, get("/api/reportes/asistencia-por-capacitacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas.totalElementos").value(2))
                .andExpect(jsonPath("$.filas.contenido[*].titulo", contains("Inventario de Beta", "Pasarelas de pago")))
                .andExpect(jsonPath("$.filas.contenido[0].inscritos").value(0))
                .andExpect(jsonPath("$.filas.contenido[0].realizada").value(false))
                .andExpect(jsonPath("$.filas.contenido[0].porcentajeAsistencia").value(nullValue()))
                .andExpect(jsonPath("$.filas.contenido[1].realizada").value(true))
                .andExpect(jsonPath("$.filas.contenido[1].inscritos").value(3))
                .andExpect(jsonPath("$.filas.contenido[1].asistieron").value(1))
                .andExpect(jsonPath("$.filas.contenido[1].marcadas").value(2))
                .andExpect(jsonPath("$.filas.contenido[1].porcentajeAsistencia").value(50.0));

        mvc.perform(como(agente, get("/api/reportes/asistencia-por-capacitacion").param("modulo", "VENTAS_ONLINE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas.totalElementos").value(1))
                .andExpect(jsonPath("$.filas.contenido[*].titulo", contains("Pasarelas de pago")));
    }

    // ---------- A7 ----------

    @Test
    void a7CuentaTicketsDistintosYResueltosPorModulo() throws Exception {
        mvc.perform(como(agente, get("/api/reportes/articulos-por-modulo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].modulo", contains("INVENTARIOS", "VENTAS_ONLINE")))
                .andExpect(jsonPath("$.filas[0].articulos").value(1))
                .andExpect(jsonPath("$.filas[0].ticketsVinculados").value(0))
                .andExpect(jsonPath("$.filas[1].articulos").value(2))
                .andExpect(jsonPath("$.filas[1].publicos").value(1))
                .andExpect(jsonPath("$.filas[1].internos").value(1))
                .andExpect(jsonPath("$.filas[1].conVector").value(1))
                .andExpect(jsonPath("$.filas[1].ticketsVinculados").value(2))
                .andExpect(jsonPath("$.filas[1].ticketsResueltos").value(1))
                .andExpect(jsonPath("$.filas[1].resueltosPorArticulo").value(0.5));
    }

    // ---------- A8 ----------

    @Test
    void a8SoloSuspendidasConTicketsAbiertos() throws Exception {
        mvc.perform(como(admin, get("/api/reportes/suspendidas-con-tickets-abiertos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].empresaId").value(beta.getId().toHexString()))
                .andExpect(jsonPath("$.filas[0].empresa").value("Beta SAS"))
                .andExpect(jsonPath("$.filas[0].plan").value("Premium"))
                .andExpect(jsonPath("$.filas[0].ticketsAbiertos").value(2))
                .andExpect(jsonPath("$.filas[0].abiertoMasAntiguo").value(t4.getFechaCreacion().toString()));
    }

    // ---------- A9 ----------

    @Test
    void a9ComparaLasDosVentanasDeLaSesion() throws Exception {
        // Sesión que terminó hace 80 días; ventana de 30: antes [-110, -80), después [-80, -50).
        Capacitacion sesion = capacitacion("Ventas online avanzado", TipoCapacitacion.MASIVA, ModuloErp.VENTAS_ONLINE,
                null, hace(80).minus(Duration.ofMinutes(60)), 2);
        inscripcion(sesion, clienteX, true);
        inscripcion(sesion, clienteY, false);
        ticket(clienteX, ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, EstadoTicket.CERRADO, null, hace(120), null);
        ticket(clienteX, ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, EstadoTicket.CERRADO, null, hace(100), null);
        ticket(clienteX, ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, EstadoTicket.CERRADO, null, hace(90), null);
        ticket(clienteX, ModuloErp.VENTAS_ONLINE, TipoTicket.INCIDENTE, EstadoTicket.CERRADO, null, hace(60), null);

        mvc.perform(como(admin, get("/api/reportes/impacto-capacitaciones").param("dias", "30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.dias").value(30))
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].modulo").value("VENTAS_ONLINE"))
                .andExpect(jsonPath("$.filas[0].asistencias").value(1))
                .andExpect(jsonPath("$.filas[0].antes").value(2))
                .andExpect(jsonPath("$.filas[0].despues").value(1))
                .andExpect(jsonPath("$.filas[0].variacionPorcentual").value(-50.0))
                .andExpect(jsonPath("$.resumen.fechaFinDesde").value(hace(90).toString()))
                .andExpect(jsonPath("$.resumen.fechaFinHasta").value(hace(32).toString()));

        // Con 90 días la historia (120 días) no alcanza para dos ventanas completas.
        mvc.perform(como(admin, get("/api/reportes/impacto-capacitaciones")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.dias").value(90))
                .andExpect(jsonPath("$.filas", hasSize(0)));
    }

    @Test
    void a9ValidaLaVentana() throws Exception {
        mvc.perform(como(admin, get("/api/reportes/impacto-capacitaciones").param("dias", "29")))
                .andExpect(status().isBadRequest());
        mvc.perform(como(admin, get("/api/reportes/impacto-capacitaciones").param("dias", "181")))
                .andExpect(status().isBadRequest());
    }

    // ---------- utilidades ----------

    private Instant hace(int dias) {
        return ahora.minus(Duration.ofDays(dias));
    }

    private static Instant mitadDe(YearMonth mes) {
        return mes.atDay(15).atTime(12, 0).atZone(BOGOTA).toInstant();
    }

    private static BigDecimal dinero(MvcResult r, String ruta) throws Exception {
        Object valor = JsonPath.read(r.getResponse().getContentAsString(), ruta);
        return new BigDecimal(valor.toString());
    }

    private String token(Usuario u) {
        Instant t = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(u.getId().toHexString())
                .issuedAt(t).expiresAt(t.plusSeconds(900)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private MockHttpServletRequestBuilder como(Usuario u, MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(u));
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de ReportesIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(true);
        r.setEsSistema(false);
        return template.insert(r);
    }

    private Usuario usuario(String nombre, String email, ObjectId empresaId, Rol rol) {
        Usuario u = new Usuario();
        u.setFechaCreacion(ahora);
        u.setFechaActualizacion(ahora);
        u.setNombre(nombre);
        u.setEmail(email);
        u.setPasswordHash(HASH);
        u.setRolIds(List.of(rol.getId()));
        u.setEmpresaId(empresaId);
        u.setActivo(true);
        return template.insert(u);
    }

    private Empresa empresa(String nit, String razonSocial) {
        Empresa e = new Empresa();
        e.setFechaCreacion(ahora);
        e.setFechaActualizacion(ahora);
        e.setNit(nit);
        e.setRazonSocial(razonSocial);
        e.setContacto(new Contacto("Contacto " + razonSocial, "contacto@empresa.co", "3001234567", null, null));
        return template.insert(e);
    }

    private Plan plan(String nombre, NivelAcceso nivel, String precio, boolean activo) {
        Plan p = new Plan();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setNombre(nombre);
        p.setDescripcion("Plan de ReportesIntegracionTest");
        p.setPrecio(new BigDecimal(precio));
        p.setNivelAcceso(nivel);
        p.setLimites(new LimitesPlan(48, 1, 5, 5));
        p.setActivo(activo);
        return template.insert(p);
    }

    private Membresia membresia(Empresa empresa, Plan plan, EstadoMembresia estado, Instant vence,
            List<ModuloErp> modulos) {
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(empresa.getId());
        m.setPlanId(plan.getId());
        m.setModulosContratados(modulos);
        m.setFechaInicio(vence.minus(Duration.ofDays(365)));
        m.setFechaVencimiento(vence);
        m.setEstado(estado);
        return template.insert(m);
    }

    private Pago pago(Membresia membresia, String monto, ResultadoPago resultado, Instant fecha) {
        Pago p = new Pago();
        p.setFechaCreacion(fecha);
        p.setFechaActualizacion(fecha);
        p.setMembresiaId(membresia.getId());
        p.setEmpresaId(membresia.getEmpresaId());
        p.setMonto(new BigDecimal(monto));
        p.setFechaPago(fecha);
        p.setMetodoPago(MetodoPago.PSE);
        p.setReferencia("REF-" + fecha.toEpochMilli());
        p.setResultado(resultado);
        p.setRegistradoPorId(admin == null ? new ObjectId() : admin.getId());
        return template.insert(p);
    }

    /** Ticket con {@code _id} de su misma fecha, como la semilla. {@code resolucion} null: sin fechaResolucion. */
    private Ticket ticket(Usuario solicitante, ModuloErp modulo, TipoTicket tipo, EstadoTicket estado, Usuario agenteAsignado,
            Instant creado, Duration resolucion) {
        Ticket t = new Ticket();
        t.setId(new ObjectId(Date.from(creado)));
        t.setFechaCreacion(creado);
        t.setFechaActualizacion(creado);
        t.setEmpresaId(solicitante.getEmpresaId());
        t.setCreadorId(solicitante.getId());
        t.setSolicitanteId(solicitante.getId());
        t.setAgenteId(agenteAsignado == null ? null : agenteAsignado.getId());
        t.setModulo(modulo);
        t.setTipo(tipo);
        t.setTitulo("Ticket de " + modulo);
        t.setDescripcion("Ticket de ReportesIntegracionTest");
        t.setDetalle(tipo == TipoTicket.SUGERENCIA ? null : detalle(modulo));
        t.setEstado(estado);
        t.setHistorialEstados(new ArrayList<>(List.of(new CambioEstado(EstadoTicket.ABIERTO, creado, solicitante.getId()))));
        t.setFechaResolucion(resolucion == null ? null : creado.plus(resolucion));
        t.setFechaPrimeraAsignacion(agenteAsignado == null ? null : creado);
        t.setFechaLimiteRespuesta(creado.plus(Duration.ofHours(48)));
        return template.insert(t);
    }

    private static DetalleTicket detalle(ModuloErp modulo) {
        return switch (modulo) {
            case VENTAS_ONLINE -> new DetalleVentasOnline("PAS-1", "https://tienda.alfa.co/pago", "Chrome 130", null);
            case INVENTARIOS -> new DetalleInventarios("BOD-1", "PROD-1", DetalleInventarios.TipoOperacion.SALIDA,
                    null, null);
            default -> throw new IllegalArgumentException("La prueba solo usa Ventas Online e Inventarios");
        };
    }

    private Capacitacion capacitacion(String titulo, TipoCapacitacion tipo, ModuloErp modulo, ObjectId empresaId,
            Instant inicio, int cuposOcupados) {
        Capacitacion c = new Capacitacion();
        c.setFechaCreacion(ahora);
        c.setFechaActualizacion(ahora);
        c.setTitulo(titulo);
        c.setTipo(tipo);
        c.setModulo(modulo);
        c.setEmpresaId(empresaId);
        c.setFechaInicio(inicio);
        c.setDuracionMinutos(60);
        c.setCupo(10);
        c.setCuposOcupados(cuposOcupados);
        c.setInstructorId(agente.getId());
        c.setEstado(EstadoCapacitacion.PROGRAMADA);
        c.setSolicitudId(null);
        return template.insert(c);
    }

    /** {@code asistio} null: sin marcar. */
    private Inscripcion inscripcion(Capacitacion capacitacion, Usuario usuario, Boolean asistio) {
        Inscripcion i = new Inscripcion();
        i.setFechaCreacion(ahora);
        i.setFechaActualizacion(ahora);
        i.setCapacitacionId(capacitacion.getId());
        i.setUsuarioId(usuario.getId());
        i.setEmpresaId(usuario.getEmpresaId());
        if (asistio == null) {
            i.quitarMarca();
        } else {
            i.marcarAsistencia(asistio, agente.getId(), capacitacion.getFechaFin());
        }
        return template.insert(i);
    }

    private Articulo articulo(String titulo, ModuloErp modulo, Visibilidad visibilidad, List<ObjectId> tickets,
            boolean conVector) {
        Articulo a = new Articulo();
        a.setFechaCreacion(ahora);
        a.setFechaActualizacion(ahora);
        a.setTitulo(titulo);
        a.setContenido("Contenido de " + titulo);
        a.setModulo(modulo);
        a.setEtiquetas(new ArrayList<>(List.of("prueba")));
        a.setVisibilidad(visibilidad);
        a.setTicketIds(new ArrayList<>(tickets));
        a.setAutorId(agente.getId());
        if (conVector) {
            a.asignarEmbedding(ProveedorEmbeddingsFalso.vector(titulo), GeminiEmbeddings.MODELO);
        } else {
            a.marcarEmbeddingPendiente();
        }
        return template.insert(a);
    }
}
