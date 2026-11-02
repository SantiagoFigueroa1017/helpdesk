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
backend/         (Fase 2)
frontend/        (Fase 3)
```

## Base de datos

La base se llama `helpdesk` y tiene 13 colecciones. Cada colección se crea con su script de validación. Si se crea con un insert suelto, MongoDB la deja sin validador.

1. Abrir MongoDB Compass y conectarse a Atlas o a `mongodb://localhost:27017/?directConnection=true`.
2. Abrir la consola mongosh de Compass y pegar `database/validadores/00_todos_los_validadores.js`.
3. Para comprobar, pegar `database/pruebas/00_pruebas_12_colecciones.js`, que debe mostrar `69/69`, y luego `database/pruebas/07_tickets_pruebas.js`, que debe mostrar `17/17`.

Los scripts se pueden correr varias veces: si la colección ya existe, actualizan su validador con `collMod`. El usuario de la conexión necesita el rol `dbAdmin` sobre `helpdesk`.

### Índices

Se crean después de los validadores, en la misma consola:

1. `database/indices/00_indices.js` crea los 32 índices (únicos, compuestos, multikey, de texto y TTL). Debe terminar en `RESULTADO: OK`.
2. `database/pruebas/indices_unicos_pruebas.js` intenta insertar duplicados contra los 7 índices únicos. Debe mostrar `21/21`.
3. `database/indices/02_verificar_indices.js` corre la consulta de cada índice con `explain("executionStats")` y muestra qué índice eligió MongoDB.
4. Solo en Atlas: `database/indices/01_indice_vectorial_atlas.js` crea `vec_articulos_embedding`. Se revisa con `db.getSiblingDB('helpdesk').articulos.getSearchIndexes()` hasta ver `status: 'READY'`. En local el script no hace nada.

Los nombres de índice empiezan por `ux_` (único), `ix_` (normal), `txt_` (texto) o `ttl_` (expiración), y así aparecen en `explain`.

Este README se completa en la tarea 4.1 con el despliegue del backend y el frontend.

## Secretos

La cadena de conexión de Atlas, la clave JWT y `GEMINI_API_KEY` van en variables de entorno. Nunca se suben al repositorio.
