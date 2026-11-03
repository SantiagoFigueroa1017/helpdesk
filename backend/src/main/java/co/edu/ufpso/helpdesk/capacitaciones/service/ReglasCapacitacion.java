package co.edu.ufpso.helpdesk.capacitaciones.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;

import co.edu.ufpso.helpdesk.capacitaciones.mapper.CapacitacionMapper;
import co.edu.ufpso.helpdesk.common.dominio.ModuloErp;
import co.edu.ufpso.helpdesk.common.error.ReglaNegocioException;
import co.edu.ufpso.helpdesk.membresias.service.ContratoEmpresa;

/** Reglas que comparten capacitaciones y solicitudes (tarea 2.8). */
final class ReglasCapacitacion {

    /** Mismos nombres que muestra el formulario de tickets ({@code CatalogoDetalle}). */
    private static final Map<ModuloErp, String> ETIQUETAS = new EnumMap<>(Map.of(
            ModuloErp.VENTAS_ONLINE, "Ventas Online",
            ModuloErp.INVENTARIOS, "Manejo de Inventarios",
            ModuloErp.COMPRAS, "Compras",
            ModuloErp.PRODUCCION, "Producción",
            ModuloErp.VENTAS_EN_SITIO, "Ventas en Sitio (Caja/POS)"));

    private ReglasCapacitacion() {
    }

    /** El módulo debe estar en la membresía (igual que HU21 para tickets): 422. */
    static void verificarModuloContratado(ModuloErp modulo, ContratoEmpresa contrato) {
        if (!contrato.modulos().contains(modulo)) {
            String contratados = EnumSet.copyOf(contrato.modulos()).stream()
                    .map(ETIQUETAS::get)
                    .collect(Collectors.joining(", "));
            throw new ReglaNegocioException("La empresa no tiene contratado el módulo " + ETIQUETAS.get(modulo)
                    + ". Módulos contratados: " + contratados + ".");
        }
    }

    /**
     * Comienzo del mes pagado vigente (decidido en 2.8): un mes de calendario antes de
     * {@code fechaVencimiento}, en hora de Bogotá, sin pasar de {@code fechaInicio}. Cada pago aprobado
     * suma un mes a la vigencia (2.5), así que el cupo de {@code capacitacionesIncluidas} se renueva con
     * cada pago.
     */
    static Instant inicioMesPagado(ContratoEmpresa contrato) {
        Instant unMesAntes = contrato.fechaVencimiento().atZone(CapacitacionMapper.BOGOTA).minusMonths(1).toInstant();
        return unMesAntes.isAfter(contrato.fechaInicio()) ? unMesAntes : contrato.fechaInicio();
    }
}
