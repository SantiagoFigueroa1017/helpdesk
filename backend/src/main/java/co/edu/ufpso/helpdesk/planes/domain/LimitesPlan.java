package co.edu.ufpso.helpdesk.planes.domain;

/**
 * Límites del plan, embebidos en {@code planes.limites} (HU09 v.4). Los cuatro son int32 y
 * obligatorios. Se verifican solo cuando una acción nueva los superaría; bajarlos en HU11 no borra
 * nada (contexto 7.5).
 *
 * @param horasRespuesta          mayor que cero. Fija {@code tickets.fechaLimiteRespuesta} al crear
 * @param capacitacionesIncluidas 0 o más. Tope de solicitudes PENDIENTE o PROGRAMADA por vigencia
 * @param maxUsuarios             mayor que cero. Usuarios activos de la empresa
 * @param maxModulos              de 1 a 5
 */
public record LimitesPlan(Integer horasRespuesta, Integer capacitacionesIncluidas, Integer maxUsuarios,
        Integer maxModulos) {
}
