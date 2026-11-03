package co.edu.ufpso.helpdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa {@code @Scheduled} (tarea 2.7). Hoy hay una sola tarea programada:
 * {@code conocimiento.service.RegeneracionEmbeddings}, que genera cada 10 minutos los vectores pendientes.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
