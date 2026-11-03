package co.edu.ufpso.helpdesk.membresias.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mongodb.client.MongoClient;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
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
import co.edu.ufpso.helpdesk.soporte.ValidadoresDePrueba;
import co.edu.ufpso.helpdesk.usuarios.domain.Usuario;

/**
 * Membresías (HU13 a HU16), pagos (HU17 a HU20) y la transacción de bloqueo por falta de pago
 * (contexto 7.5) de punta a punta, con token real y validadores reales (tarea 2.5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembresiasPagosIntegracionTest {

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
    private Usuario usuarioAdmin;
    private Usuario usuarioClienteAdmin;
    private Empresa ferreteria;
    private Empresa panaderia;
    private Plan basico;
    private Plan inactivo;
    private Membresia membresiaFerreteria;

    @BeforeEach
    void prepararDatos() {
        for (String coleccion : List.of("roles", "usuarios", "empresas", "planes", "membresias", "pagos")) {
            ValidadoresDePrueba.copiar(mongoClient, template, coleccion);
        }
        ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ferreteria = empresa("900123456-1", "Ferretería El Tornillo");
        panaderia = empresa("800765432-9", "Panadería La Espiga");
        basico = plan("Básico", true, 2);
        inactivo = plan("Retirado", false, 5);
        membresiaFerreteria = membresia(ferreteria.getId(), EstadoMembresia.ACTIVA, ahora.plus(10, ChronoUnit.DAYS),
                ModuloErp.INVENTARIOS, ModuloErp.COMPRAS);

        Rol admin = rol("ADMIN", AlcanceRol.GLOBAL, Arrays.asList(Permiso.values()));
        Rol clienteAdmin = rol("CLIENTE_ADMIN", AlcanceRol.EMPRESA, List.of(Permiso.MEMBRESIAS_VER, Permiso.PAGOS_VER));
        usuarioAdmin = usuario("Liliana Contreras", "liliana@sistemasintegrados.com.co", null, admin);
        usuarioClienteAdmin = usuario("Camilo Quintero", "camilo@ferreteria.co", ferreteria.getId(), clienteAdmin);
    }

    // ---------- utilidades ----------

    private Empresa empresa(String nit, String razonSocial) {
        Empresa e = new Empresa();
        e.setFechaCreacion(ahora);
        e.setFechaActualizacion(ahora);
        e.setNit(nit);
        e.setRazonSocial(razonSocial);
        e.setContacto(new Contacto("Contacto", "contacto@empresa.co", "3001234567", null, null));
        return template.insert(e);
    }

    private Plan plan(String nombre, boolean activo, int maxModulos) {
        Plan p = new Plan();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setNombre(nombre);
        p.setDescripcion("Plan de prueba");
        p.setPrecio(new BigDecimal("150000"));
        p.setNivelAcceso(NivelAcceso.BASICO);
        p.setLimites(new LimitesPlan(48, 1, 5, maxModulos));
        p.setActivo(activo);
        return template.insert(p);
    }

    private Membresia membresia(ObjectId empresaId, EstadoMembresia estado, Instant vencimiento, ModuloErp... modulos) {
        Membresia m = new Membresia();
        m.setFechaCreacion(ahora);
        m.setFechaActualizacion(ahora);
        m.setEmpresaId(empresaId);
        m.setPlanId(basico.getId());
        m.setModulosContratados(List.of(modulos));
        m.setFechaInicio(ahora.minus(60, ChronoUnit.DAYS));
        m.setFechaVencimiento(vencimiento);
        m.setEstado(estado);
        return template.insert(m);
    }

    private Pago pago(Membresia m, Instant fechaPago, ResultadoPago resultado) {
        Pago p = new Pago();
        p.setFechaCreacion(ahora);
        p.setFechaActualizacion(ahora);
        p.setMembresiaId(m.getId());
        p.setEmpresaId(m.getEmpresaId());
        p.setMonto(new BigDecimal("150000"));
        p.setFechaPago(fechaPago);
        p.setMetodoPago(MetodoPago.PSE);
        p.setReferencia("REF-" + fechaPago.toEpochMilli());
        p.setResultado(resultado);
        p.setRegistradoPorId(usuarioAdmin.getId());
        return template.insert(p);
    }

    private Rol rol(String nombre, AlcanceRol alcance, List<Permiso> permisos) {
        Rol r = new Rol();
        r.setFechaCreacion(ahora);
        r.setFechaActualizacion(ahora);
        r.setNombre(nombre);
        r.setDescripcion("Rol de MembresiasPagosIntegracionTest");
        r.setAlcance(alcance);
        r.setPermisos(permisos);
        r.setActivo(true);
        r.setEsSistema(true);
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

    private static String cuerpoMembresia(ObjectId empresaId, ObjectId planId, String modulos, Instant inicio,
            Instant vencimiento) {
        return "{\"empresaId\":\"" + empresaId + "\",\"planId\":\"" + planId + "\",\"modulosContratados\":[" + modulos
                + "],\"fechaInicio\":\"" + inicio + "\",\"fechaVencimiento\":\"" + vencimiento + "\"}";
    }

    private static String cuerpoPago(ObjectId membresiaId, Instant fechaPago, String resultado) {
        return "{\"membresiaId\":\"" + membresiaId + "\",\"monto\":150000,\"fechaPago\":\"" + fechaPago
                + "\",\"metodoPago\":\"PSE\",\"referencia\":\"PSE-001\",\"resultado\":\"" + resultado + "\"}";
    }

    private static String cuerpoEdicionPago(Instant fechaPago, String resultado) {
        return "{\"monto\":150000,\"fechaPago\":\"" + fechaPago
                + "\",\"metodoPago\":\"PSE\",\"referencia\":\"PSE-001\",\"resultado\":\"" + resultado + "\"}";
    }

    private Membresia leer(Membresia m) {
        return template.findById(m.getId(), Membresia.class);
    }

    private static Instant unMesDespues(Instant fecha) {
        return fecha.atZone(BOGOTA).plusMonths(1).toInstant();
    }

    // ---------- HU13 · crear ----------

    @Test
    void laMembresiaNacePendienteYSinSoporte() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/membresias")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMembresia(panaderia.getId(), basico.getId(), "\"COMPRAS\",\"INVENTARIOS\"", ahora,
                                ahora.plus(30, ChronoUnit.DAYS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estadoVisible").value("PENDIENTE"))
                .andExpect(jsonPath("$.soporte.habilitado").value(false))
                .andExpect(jsonPath("$.soporte.motivo").value("PENDIENTE"))
                // En el orden del enum, no en el de la petición.
                .andExpect(jsonPath("$.modulosContratados[0]").value("INVENTARIOS"));
    }

    @Test
    void unaEmpresaSoloTieneUnaMembresia() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/membresias")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMembresia(ferreteria.getId(), basico.getId(), "\"COMPRAS\"", ahora,
                                ahora.plus(30, ChronoUnit.DAYS))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void lasReglasDelPlanYLasFechasResponden422() throws Exception {
        Instant fin = ahora.plus(30, ChronoUnit.DAYS);
        mvc.perform(como(usuarioAdmin, post("/api/membresias")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMembresia(panaderia.getId(), inactivo.getId(), "\"COMPRAS\"", ahora, fin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("inactivo")));
        mvc.perform(como(usuarioAdmin, post("/api/membresias")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMembresia(panaderia.getId(), basico.getId(),
                                "\"COMPRAS\",\"INVENTARIOS\",\"PRODUCCION\"", ahora, fin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("como máximo 2")));
        mvc.perform(como(usuarioAdmin, post("/api/membresias")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMembresia(panaderia.getId(), basico.getId(), "\"COMPRAS\"", fin, ahora)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        mvc.perform(como(usuarioAdmin, post("/api/membresias")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMembresia(new ObjectId(), basico.getId(), "\"COMPRAS\"", ahora, fin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("empresa")));
    }

    // ---------- HU14 · consultar ----------

    @Test
    void unUsuarioDeEmpresaSoloVeLaMembresiaDeLaSuya() throws Exception {
        Membresia otra = membresia(panaderia.getId(), EstadoMembresia.PENDIENTE, ahora.plus(5, ChronoUnit.DAYS),
                ModuloErp.COMPRAS);
        mvc.perform(como(usuarioClienteAdmin, get("/api/membresias").param("empresaId", panaderia.getId().toHexString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].empresaId").value(ferreteria.getId().toHexString()))
                .andExpect(jsonPath("$.contenido[0].soporte.habilitado").value(true));
        mvc.perform(como(usuarioClienteAdmin, get("/api/membresias/" + otra.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaActivaVencidaSeMuestraComoVencida() throws Exception {
        Membresia vencida = membresia(panaderia.getId(), EstadoMembresia.ACTIVA, ahora.minus(1, ChronoUnit.DAYS),
                ModuloErp.COMPRAS);
        mvc.perform(como(usuarioAdmin, get("/api/membresias/" + vencida.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.estadoVisible").value("VENCIDA"))
                .andExpect(jsonPath("$.soporte.motivo").value("VENCIDA"));
    }

    @Test
    void unIdMalFormadoEnElFiltroResponde400() throws Exception {
        mvc.perform(como(usuarioAdmin, get("/api/membresias").param("empresaId", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    // ---------- HU15 · actualizar ----------

    @Test
    void agregarModulosPorEncimaDelPlanResponde422PeroCambiarAUnPlanMenorNoQuitaNada() throws Exception {
        Instant inicio = membresiaFerreteria.getFechaInicio();
        Instant fin = membresiaFerreteria.getFechaVencimiento();
        String cuerpo = "{\"planId\":\"%s\",\"modulosContratados\":[%s],\"fechaInicio\":\"" + inicio
                + "\",\"fechaVencimiento\":\"" + fin + "\",\"estado\":\"ACTIVA\"}";

        mvc.perform(como(usuarioAdmin, put("/api/membresias/" + membresiaFerreteria.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted(basico.getId(), "\"INVENTARIOS\",\"COMPRAS\",\"PRODUCCION\"")))
                .andExpect(status().isUnprocessableContent());

        Plan pequeno = plan("Pequeño", true, 1);
        mvc.perform(como(usuarioAdmin, put("/api/membresias/" + membresiaFerreteria.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted(pequeno.getId(), "\"INVENTARIOS\",\"COMPRAS\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(pequeno.getId().toHexString()))
                .andExpect(jsonPath("$.modulosContratados.length()").value(2));
    }

    // ---------- HU16 · eliminar ----------

    @Test
    void unaMembresiaConPagosNoSeEliminaYSinPagosSi() throws Exception {
        pago(membresiaFerreteria, ahora.minus(1, ChronoUnit.DAYS), ResultadoPago.APROBADO);
        mvc.perform(como(usuarioAdmin, delete("/api/membresias/" + membresiaFerreteria.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.dependientes.pagos").value(1));

        Membresia sinPagos = membresia(panaderia.getId(), EstadoMembresia.PENDIENTE, ahora.plus(5, ChronoUnit.DAYS),
                ModuloErp.COMPRAS);
        mvc.perform(como(usuarioAdmin, delete("/api/membresias/" + sinPagos.getId())))
                .andExpect(status().isNoContent());
    }

    // ---------- HU17 · registrar y transacción de 7.5 ----------

    @Test
    void unPagoFallidoSuspendeLaMembresiaSinTocarSuVencimiento() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(membresiaFerreteria.getId(), ahora, "FALLIDO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(ferreteria.getId().toHexString()))
                .andExpect(jsonPath("$.registradoPorId").value(usuarioAdmin.getId().toHexString()));

        Membresia despues = leer(membresiaFerreteria);
        assertThat(despues.getEstado()).isEqualTo(EstadoMembresia.SUSPENDIDA);
        assertThat(despues.getFechaVencimiento()).isEqualTo(membresiaFerreteria.getFechaVencimiento());

        // La siguiente consulta del cliente ya ve el bloqueo.
        mvc.perform(como(usuarioClienteAdmin, get("/api/membresias/" + membresiaFerreteria.getId())))
                .andExpect(jsonPath("$.soporte.motivo").value("SUSPENDIDA"));
    }

    @Test
    void unPagoAprobadoReactivaYSumaUnMesAlVencimientoSiNoHabiaVencido() throws Exception {
        Membresia suspendida = membresia(panaderia.getId(), EstadoMembresia.SUSPENDIDA,
                ahora.plus(5, ChronoUnit.DAYS), ModuloErp.COMPRAS);
        mvc.perform(como(usuarioAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(suspendida.getId(), ahora, "APROBADO")))
                .andExpect(status().isCreated());

        Membresia despues = leer(suspendida);
        assertThat(despues.getEstado()).isEqualTo(EstadoMembresia.ACTIVA);
        assertThat(despues.getFechaVencimiento()).isEqualTo(unMesDespues(suspendida.getFechaVencimiento()));
    }

    @Test
    void unPagoAprobadoSobreUnaVencidaCuentaElMesDesdeLaFechaDelPago() throws Exception {
        Membresia vencida = membresia(panaderia.getId(), EstadoMembresia.ACTIVA, ahora.minus(20, ChronoUnit.DAYS),
                ModuloErp.COMPRAS);
        Instant fechaPago = ahora.minus(1, ChronoUnit.HOURS);
        mvc.perform(como(usuarioAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(vencida.getId(), fechaPago, "APROBADO")))
                .andExpect(status().isCreated());

        Membresia despues = leer(vencida);
        assertThat(despues.getEstado()).isEqualTo(EstadoMembresia.ACTIVA);
        assertThat(despues.getFechaVencimiento()).isEqualTo(unMesDespues(fechaPago));
    }

    @Test
    void siLaSegundaEscrituraFallaNoQuedaNingunaDeLas2() throws Exception {
        // La membresía de la base de pruebas ya no acepta SUSPENDIDA: la actualización de la
        // transacción falla después de insertar el pago.
        template.getDb().runCommand(new Document("collMod", "membresias")
                .append("validator", new Document("estado", new Document("$in", List.of("PENDIENTE", "ACTIVA")))));

        mvc.perform(como(usuarioAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(membresiaFerreteria.getId(), ahora, "FALLIDO")))
                .andExpect(status().isInternalServerError());

        assertThat(template.count(new Query(), Pago.class)).isZero();
        assertThat(leer(membresiaFerreteria).getEstado()).isEqualTo(EstadoMembresia.ACTIVA);
    }

    @Test
    void unPagoConFechaFuturaOSobreUnaMembresiaInexistenteResponde422() throws Exception {
        mvc.perform(como(usuarioAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(membresiaFerreteria.getId(), ahora.plus(1, ChronoUnit.DAYS), "APROBADO")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("futura")));
        mvc.perform(como(usuarioAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(new ObjectId(), ahora, "APROBADO")))
                .andExpect(status().isUnprocessableContent());
        assertThat(template.count(new Query(), Pago.class)).isZero();
    }

    // ---------- HU18 · consultar ----------

    @Test
    void unUsuarioDeEmpresaSoloVeSusPagosYNoRegistra() throws Exception {
        Membresia otra = membresia(panaderia.getId(), EstadoMembresia.ACTIVA, ahora.plus(5, ChronoUnit.DAYS),
                ModuloErp.COMPRAS);
        pago(membresiaFerreteria, ahora.minus(2, ChronoUnit.DAYS), ResultadoPago.APROBADO);
        pago(membresiaFerreteria, ahora.minus(1, ChronoUnit.DAYS), ResultadoPago.FALLIDO);
        Pago ajeno = pago(otra, ahora.minus(1, ChronoUnit.DAYS), ResultadoPago.APROBADO);

        mvc.perform(como(usuarioClienteAdmin, get("/api/pagos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                // Del más reciente al más antiguo.
                .andExpect(jsonPath("$.contenido[0].resultado").value("FALLIDO"));
        mvc.perform(como(usuarioClienteAdmin, get("/api/pagos/" + ajeno.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(como(usuarioClienteAdmin, post("/api/pagos")).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPago(membresiaFerreteria.getId(), ahora, "APROBADO")))
                .andExpect(status().isForbidden());
    }

    // ---------- HU19 · actualizar ----------

    @Test
    void corregirElPagoMasRecienteAFallidoSuspendeSinDevolverElMes() throws Exception {
        Pago ultimo = pago(membresiaFerreteria, ahora.minus(1, ChronoUnit.DAYS), ResultadoPago.APROBADO);
        mvc.perform(como(usuarioAdmin, put("/api/pagos/" + ultimo.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicionPago(ultimo.getFechaPago(), "FALLIDO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("FALLIDO"));

        Membresia despues = leer(membresiaFerreteria);
        assertThat(despues.getEstado()).isEqualTo(EstadoMembresia.SUSPENDIDA);
        assertThat(despues.getFechaVencimiento()).isEqualTo(membresiaFerreteria.getFechaVencimiento());
    }

    @Test
    void corregirUnPagoViejoNoTocaLaMembresiaActual() throws Exception {
        Pago viejo = pago(membresiaFerreteria, ahora.minus(40, ChronoUnit.DAYS), ResultadoPago.APROBADO);
        pago(membresiaFerreteria, ahora.minus(10, ChronoUnit.DAYS), ResultadoPago.APROBADO);

        mvc.perform(como(usuarioAdmin, put("/api/pagos/" + viejo.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoEdicionPago(viejo.getFechaPago(), "FALLIDO")))
                .andExpect(status().isOk());

        Membresia despues = leer(membresiaFerreteria);
        assertThat(despues.getEstado()).isEqualTo(EstadoMembresia.ACTIVA);
        assertThat(despues.getFechaVencimiento()).isEqualTo(membresiaFerreteria.getFechaVencimiento());
    }

    // ---------- HU20 · eliminar ----------

    @Test
    void eliminarUnPagoNoCambiaLaMembresia() throws Exception {
        Pago fallido = pago(membresiaFerreteria, ahora.minus(1, ChronoUnit.DAYS), ResultadoPago.FALLIDO);
        mvc.perform(como(usuarioAdmin, delete("/api/pagos/" + fallido.getId())))
                .andExpect(status().isNoContent());
        assertThat(template.findById(fallido.getId(), Pago.class)).isNull();
        assertThat(leer(membresiaFerreteria).getEstado()).isEqualTo(EstadoMembresia.ACTIVA);
    }
}
