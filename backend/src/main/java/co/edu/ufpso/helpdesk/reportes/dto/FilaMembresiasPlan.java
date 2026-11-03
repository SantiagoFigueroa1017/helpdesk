package co.edu.ufpso.helpdesk.reportes.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fila de A2: empresas con membresía ACTIVA en un plan. {@code vencidas} son las ACTIVA cuya
 * {@code fechaVencimiento} ya pasó, es decir, sin soporte (contexto 7.5).
 *
 * @param empresas razón social de las empresas con membresía ACTIVA, en orden alfabético
 */
public record FilaMembresiasPlan(String planId, String plan, String nivelAcceso, BigDecimal precio, boolean activo,
        int empresasActivas, int vigentes, int vencidas, List<String> empresas) {
}
