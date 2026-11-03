package co.edu.ufpso.helpdesk.membresias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;
import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.membresias.domain.EstadoMembresia;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.membresias.repository.MembresiaRepository;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/** Verificación central de soporte (contexto 7.5, tarea 2.5). Sin Spring ni MongoDB; hora fija. */
class SoporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T12:00:00Z");

    private final MembresiaRepository repositorio = mock(MembresiaRepository.class);
    private final SoporteService soporte = new SoporteService(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
    private final ObjectId empresa = new ObjectId();

    private UsuarioAutenticado deEmpresa() {
        return new UsuarioAutenticado(new ObjectId(), "Camilo Quintero", "camilo@ferreteria.co", empresa,
                AlcanceRol.EMPRESA, Set.of(Permiso.TICKETS_CREAR));
    }

    private void membresia(EstadoMembresia estado, Instant vencimiento) {
        Membresia m = new Membresia();
        m.setEstado(estado);
        m.setFechaVencimiento(vencimiento);
        when(repositorio.buscarParaSoporte(empresa)).thenReturn(Optional.of(m));
    }

    private String motivoDelBloqueo() {
        Throwable error = catchThrowable(() -> soporte.exigirSoporte(deEmpresa()));
        assertThat(error).isInstanceOf(ApiException.class);
        ApiException ex = (ApiException) error;
        assertThat(ex.getCodigo()).isEqualTo(CodigoError.SOPORTE_BLOQUEADO);
        return (String) ex.getPropiedades().get("motivo");
    }

    @Test
    void unUsuarioGlobalPasaSinConsultarLaBase() {
        UsuarioAutenticado agente = new UsuarioAutenticado(new ObjectId(), "Jhon Pacheco", "jhon@si.com.co", null,
                AlcanceRol.GLOBAL, Set.of(Permiso.TICKETS_CREAR));
        assertThatCode(() -> soporte.exigirSoporte(agente)).doesNotThrowAnyException();
        verify(repositorio, never()).buscarParaSoporte(any());
    }

    @Test
    void activaYVigentePasa() {
        membresia(EstadoMembresia.ACTIVA, AHORA.plusSeconds(1));
        assertThatCode(() -> soporte.exigirSoporte(deEmpresa())).doesNotThrowAnyException();
        assertThat(soporte.estado(empresa)).isEqualTo(new EstadoSoporte(true, null));
    }

    @Test
    void elMismoInstanteDelVencimientoTodaviaTieneSoporte() {
        membresia(EstadoMembresia.ACTIVA, AHORA);
        assertThatCode(() -> soporte.exigirSoporte(deEmpresa())).doesNotThrowAnyException();
    }

    @Test
    void sinMembresiaSeBloquea() {
        when(repositorio.buscarParaSoporte(empresa)).thenReturn(Optional.empty());
        assertThat(motivoDelBloqueo()).isEqualTo("SIN_MEMBRESIA");
    }

    @Test
    void pendienteSeBloquea() {
        membresia(EstadoMembresia.PENDIENTE, AHORA.plusSeconds(3600));
        assertThat(motivoDelBloqueo()).isEqualTo("PENDIENTE");
    }

    @Test
    void suspendidaSeBloqueaAunqueNoHayaVencido() {
        membresia(EstadoMembresia.SUSPENDIDA, AHORA.plusSeconds(3600));
        assertThat(motivoDelBloqueo()).isEqualTo("SUSPENDIDA");
    }

    @Test
    void activaVencidaSeBloqueaComoVencida() {
        membresia(EstadoMembresia.ACTIVA, AHORA.minusMillis(1));
        assertThat(motivoDelBloqueo()).isEqualTo("VENCIDA");
        assertThat(soporte.estado(empresa)).isEqualTo(new EstadoSoporte(false, "VENCIDA"));
    }

    @Test
    void elBloqueoAplicaTambienCuandoActuaUnAgenteSobreLaEmpresa() {
        membresia(EstadoMembresia.SUSPENDIDA, AHORA.plusSeconds(3600));
        Throwable error = catchThrowable(() -> soporte.exigirSoporteEmpresa(empresa));
        assertThat(error).isInstanceOf(ApiException.class);
        ApiException ex = (ApiException) error;
        assertThat(ex.getCodigo()).isEqualTo(CodigoError.SOPORTE_BLOQUEADO);
        assertThat(ex.getMessage()).contains("suspendida");
    }
}
