# Helpdesk & Customer Success

Sistema de soporte para los clientes del ERP/POS de Sistemas Integrados S.A. Proyecto de aula de Bases de Datos No Relacionales, Universidad Francisco de Paula Santander Ocaña.

Integrantes: Mauri Santiago Figueroa Machado, Merly Yineth Tarazona Sepúlveda y Karol Juliana Arévalo Pérez.

Stack: MongoDB (Atlas y local), Spring Boot y React con Vite y Tailwind CSS.

## Estructura

```
database/
  validadores/   un script $jsonSchema por colección y 00_todos_los_validadores.js
  indices/       índices normales, índice vectorial de Atlas y verificación con explain
  pruebas/       inserciones válidas e inválidas para cada validador y para los índices únicos
  semilla/       datos de prueba y su verificación
  reportes/      pipelines de agregación A1 a A9 y guion de la sustentación
backend/         (Fase 2)
frontend/        (Fase 3)
```

## Base de datos

La base se llama `helpdesk` y tiene 13 colecciones. Cada colección se crea con su script de validación. Si se crea con un insert suelto, MongoDB la deja sin validador.

1. Abrir MongoDB Compass y conectarse a Atlas o a `mongodb://localhost:27017/?directConnection=true`.
2. Abrir la consola mongosh de Compass y pegar `database/validadores/00_todos_los_validadores.js`.
3. Para comprobar, pegar `database/pruebas/00_pruebas_12_colecciones.js`, que debe mostrar `70/70`, y luego `database/pruebas/07_tickets_pruebas.js`, que debe mostrar `19/19`.

Los scripts se pueden correr varias veces: si la colección ya existe, actualizan su validador con `collMod`. El usuario de la conexión necesita el rol `dbAdmin` sobre `helpdesk`.

### Índices

Se crean después de los validadores, en la misma consola:

1. `database/indices/00_indices.js` crea los 32 índices (únicos, compuestos, multikey, de texto y TTL). Debe terminar en `RESULTADO: OK`.
2. `database/pruebas/indices_unicos_pruebas.js` intenta insertar duplicados contra los 7 índices únicos. Debe mostrar `21/21`.
3. `database/indices/02_verificar_indices.js` corre la consulta de cada índice con `explain("executionStats")` y muestra qué índice eligió MongoDB.
4. Solo en Atlas: `database/indices/01_indice_vectorial_atlas.js` crea `vec_articulos_embedding`. Se revisa con `db.getSiblingDB('helpdesk').articulos.getSearchIndexes()` hasta ver `status: 'READY'`. En local el script no hace nada.

Los nombres de índice empiezan por `ux_` (único), `ix_` (normal), `txt_` (texto) o `ttl_` (expiración), y así aparecen en `explain`.

### Datos de prueba

Se cargan después de los validadores y los índices, en la misma consola:

1. `database/semilla/00_datos_semilla.js` borra los documentos de las 13 colecciones y carga los datos de prueba. Conserva validadores e índices. Debe terminar en `RESULTADO: OK`. En Atlas M0 tarda uno o dos minutos.
2. `database/semilla/01_verificar_semilla.js` cuenta los documentos, revisa las reglas que aplica el backend y, en Atlas, prueba el índice vectorial. Debe terminar en `RESULTADO: OK`. Si la fila de `$vectorSearch` falla justo después de la carga, espera unos segundos a que Atlas indexe y vuelve a correrlo.
3. `database/indices/02_verificar_indices.js` otra vez. Con datos toma valores reales para las consultas y muestra cuántos documentos examina cada una con índice y sin él.

| Colección | Documentos |
|---|---|
| roles | 5 (los 4 del sistema y `CONSULTA_REPORTES`) |
| planes | 4 (Básico, Estándar, Premium y Emprendedor, inactivo) |
| empresas | 40 |
| usuarios | 414 |
| membresias | 38 (32 activas, 2 de ellas vencidas; 5 suspendidas; 1 pendiente) |
| pagos | 444 (34 fallidos) |
| solicitudesCapacitacion | 14 |
| capacitaciones | 36 |
| inscripciones | 876 |
| tickets | 8.000 (noviembre de 2025 a octubre de 2026) |
| comentariosTicket | 5.097 |
| articulos | 40 (20 con vector sintético y 20 con `embedding: null`) |
| refreshTokens | 25 (5 ya vencidas: el índice TTL las borra en un minuto) |

Todos los usuarios tienen la contraseña `Helpdesk2026*`. El administrador es `liliana.contreras@sistemasintegrados.com.co`. Son datos ficticios: el script siempre genera los mismos, salvo las sesiones, que dependen de la hora de la carga.

### Reportes (agregaciones)

Se corren después de cargar los datos de prueba, en la misma consola. Ninguno modifica datos.

1. `database/reportes/A1_incidentes_por_modulo.js` a `A9_incidentes_antes_despues.js`, uno por uno, o `00_todos_los_reportes.js` para los nueve seguidos. Cada script imprime el reporte, comprueba el resultado contra conteos directos y muestra el `explain("executionStats")` del pipeline. Debe terminar en `RESULTADO: OK`.
2. `00_todos_los_reportes.js` se genera uniendo los nueve scripts. No se edita a mano.
3. El orden para presentarlos en la sustentación está en `database/reportes/GUION_SUSTENTACION.md`.

| Reporte | Pregunta | Colección | Etapas |
|---|---|---|---|
| A1 | ¿Qué módulo del ERP falla más y cuánto tarda en resolverse? | tickets | $match, $group, $project, $sort |
| A2 | ¿Cuántas empresas tienen cada plan con la membresía activa? | planes | $lookup, $project, $sort |
| A3 | ¿Cuántos tickets abiertos tiene cada agente? | usuarios | $match, $lookup, $project, $sort |
| A4 | ¿Qué plan tiene cada empresa y cuántos tickets ha abierto? | empresas | $lookup, $group, $project, $sort |
| A5 | ¿Cuánto se recaudó y cuántos pagos fallaron cada mes? | pagos | $match, $group, $sort |
| A6 | ¿Qué porcentaje asistió a cada capacitación? | capacitaciones | $lookup, $project, $sort |
| A7 | ¿Cuántos tickets resolvió la base de conocimientos en cada módulo? | articulos | $lookup, $group, $project |
| A8 | ¿Qué empresas suspendidas dejaron tickets abiertos? | membresias | $match, $lookup, $project |
| A9 | ¿Bajan los incidentes después de asistir a la capacitación del módulo? | inscripciones | $match, $lookup, $project, $group, $sort |

Este README se completa en la tarea 4.1 con el despliegue del backend y el frontend.

## Secretos

La cadena de conexión de Atlas, la clave JWT y `GEMINI_API_KEY` van en variables de entorno. Nunca se suben al repositorio.
