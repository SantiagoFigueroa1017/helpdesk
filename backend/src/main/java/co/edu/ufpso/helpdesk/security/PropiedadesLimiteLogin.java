package co.edu.ufpso.helpdesk.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code helpdesk.login.*} de {@code application.yml} (acta 2.4).
 *
 * @param maxFallosCorreo fallos con un mismo correo que bloquean ese correo (5)
 * @param maxFallosIp     fallos desde una misma IP que bloquean esa IP (20)
 * @param ventana         tiempo desde el primer fallo durante el que se cuentan y dura el bloqueo (15 minutos)
 */
@ConfigurationProperties("helpdesk.login")
public record PropiedadesLimiteLogin(int maxFallosCorreo, int maxFallosIp, Duration ventana) {
}
