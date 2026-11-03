package co.edu.ufpso.helpdesk.membresias.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.common.error.ApiException;
import co.edu.ufpso.helpdesk.common.error.CodigoError;
import co.edu.ufpso.helpdesk.membresias.domain.Membresia;
import co.edu.ufpso.helpdesk.membresias.domain.MotivoSinSoporte;
import co.edu.ufpso.helpdesk.membresias.repository.MembresiaRepository;
import co.edu.ufpso.helpdesk.security.UsuarioAutenticado;

/**
 * Verificación central de soporte (contexto 7.5 y 8.3, decidida en 2.5).
 *
 * <p>Regla: una empresa tiene soporte si su membresía existe, está ACTIVA y la fecha actual no
 * supera {@code fechaVencimiento}. Sin soporte, la operación responde 403 {@code SOPORTE_BLOQUEADO}
 * con el campo {@code motivo}.
 *
 * <p>Cómo se usa: la primera línea de cada método de servicio marcado "Soporte: Sí" en la matriz
 * de permisos llama a uno de los dos métodos {@code exigir...}:
 * <ul>
 *   <li>{@link #exigirSoporte(UsuarioAutenticado)}: cuando la empresa es la del propio usuario. Un
 *       usuario de alcance GLOBAL pasa sin consultar nada.</li>
 *   <li>{@link #exigirSoporteEmpresa(ObjectId)}: cuando la operación es sobre otra empresa, por
 *       ejemplo un agente que crea un ticket en nombre de un cliente o inscribe a un usuario. El
 *       bloqueo aplica también a esas operaciones (decidido en 2.5).</li>
 * </ul>
 *
 * <p>Sin caché a propósito: un pago FALLIDO suspende la membresía en la base y la siguiente
 * petición ya debe quedar bloqueada. La consulta trae dos campos por {@code ux_membresias_empresa}.
 */
@Service
public class SoporteService {

    private final MembresiaRepository repositorio;
    private final Clock reloj;

    public SoporteService(MembresiaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    /** 403 {@code SOPORTE_BLOQUEADO} si el usuario es de una empresa sin soporte. */
    public void exigirSoporte(UsuarioAutenticado usuario) {
        if (!usuario.esDeEmpresa()) {
            return;
        }
        exigirSoporteEmpresa(usuario.empresaId());
    }

    /** 403 {@code SOPORTE_BLOQUEADO} si la empresa no tiene soporte, sin importar quién actúa. */
    public void exigirSoporteEmpresa(ObjectId empresaId) {
        MotivoSinSoporte motivo = motivo(empresaId);
        if (motivo != null) {
            throw new ApiException(CodigoError.SOPORTE_BLOQUEADO, motivo.getDetalle(),
                    Map.of("motivo", motivo.name()));
        }
    }

    /** Para mostrar, sin lanzar nada (por ejemplo, el menú del frontend). */
    public EstadoSoporte estado(ObjectId empresaId) {
        MotivoSinSoporte motivo = motivo(empresaId);
        return new EstadoSoporte(motivo == null, motivo == null ? null : motivo.name());
    }

    private MotivoSinSoporte motivo(ObjectId empresaId) {
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        // Sin Optional.map: motivoSinSoporte devuelve null cuando SÍ hay soporte, y map convertiría
        // ese null en un Optional vacío, que se confundiría con "no hay membresía".
        Optional<Membresia> membresia = repositorio.buscarParaSoporte(empresaId);
        if (membresia.isEmpty()) {
            return MotivoSinSoporte.SIN_MEMBRESIA;
        }
        return membresia.get().motivoSinSoporte(ahora);
    }
}
