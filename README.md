# Helpdesk & Customer Success

Sistema de soporte para los clientes del ERP/POS de Sistemas Integrados S.A. Proyecto de aula de Bases de Datos No Relacionales, Universidad Francisco de Paula Santander Ocaña.

Integrantes: Mauri Santiago Figueroa Machado, Merly Yineth Tarazona Sepúlveda y Karol Juliana Arévalo Pérez.

Stack: MongoDB (Atlas y local), Spring Boot y React con Vite y Tailwind CSS.

## Estructura

```
database/
  validadores/   un script $jsonSchema por colección y 00_todos_los_validadores.js
  pruebas/       inserciones válidas e inválidas para cada validador
backend/         (Fase 2)
frontend/        (Fase 3)
```

## Base de datos

La base se llama `helpdesk` y tiene 13 colecciones. Cada colección se crea con su script de validación. Si se crea con un insert suelto, MongoDB la deja sin validador.

1. Abrir MongoDB Compass y conectarse a Atlas o a `mongodb://localhost:27017/?directConnection=true`.
2. Abrir la consola mongosh de Compass y pegar `database/validadores/00_todos_los_validadores.js`.
3. Para comprobar, pegar `database/pruebas/00_pruebas_12_colecciones.js`, que debe mostrar `69/69`, y luego `database/pruebas/07_tickets_pruebas.js`, que debe mostrar `17/17`.

Los scripts se pueden correr varias veces: si la colección ya existe, actualizan su validador con `collMod`. El usuario de la conexión necesita el rol `dbAdmin` sobre `helpdesk`.

Este README se completa en la tarea 4.1 con el despliegue del backend y el frontend.

## Secretos

La cadena de conexión de Atlas, la clave JWT y `GEMINI_API_KEY` van en variables de entorno. Nunca se suben al repositorio.
