/**
 * Autenticación con JWT (acta 2.4).
 *
 * <ul>
 *   <li>{@code JwtConfig}: clave HS256 desde {@code JWT_SECRET}, {@code JwtEncoder} y {@code JwtDecoder}.</li>
 *   <li>{@code PropiedadesJwt}: {@code helpdesk.jwt.*} (clave, vida del access token y del refresh token).</li>
 *   <li>{@code ErroresSeguridad}: 401 y 403 de los filtros, en el mismo ProblemDetail que el resto.</li>
 * </ul>
 *
 * <p>La cadena de filtros está en {@code config.SeguridadConfig}. El usuario autenticado y los
 * permisos efectivos (unión de los permisos de sus roles activos) se agregan en las siguientes
 * partes de 2.4.
 */
package co.edu.ufpso.helpdesk.security;
