// =====================================================================
// Validador $jsonSchema de la colección roles (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 2
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/02_roles.js
// Crea la colección o reemplaza su validador con collMod.
//
// permisos solo acepta los 49 códigos del catálogo. Si el catálogo cambia,
// hay que actualizar esta lista y volver a correr el script.
//
// El backend revisa: que no cambie el alcance con usuarios asignados,
// que los roles esSistema no cambien nombre ni alcance, y que ADMIN
// conserve ROLES_GESTIONAR (HU27).
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "roles";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const PERMISOS = [
    "EMPRESAS_CREAR", "EMPRESAS_EDITAR", "EMPRESAS_ELIMINAR", "EMPRESAS_EDITAR_CONTACTO", "EMPRESAS_VER",
    "USUARIOS_CREAR", "USUARIOS_EDITAR", "USUARIOS_ELIMINAR", "USUARIOS_VER",
    "PLANES_CREAR", "PLANES_EDITAR", "PLANES_ELIMINAR", "PLANES_VER",
    "MEMBRESIAS_CREAR", "MEMBRESIAS_EDITAR", "MEMBRESIAS_ELIMINAR", "MEMBRESIAS_VER",
    "PAGOS_REGISTRAR", "PAGOS_EDITAR", "PAGOS_ELIMINAR", "PAGOS_VER",
    "TICKETS_CREAR", "TICKETS_VER", "TICKETS_VER_PROPIOS", "TICKETS_EDITAR", "TICKETS_EDITAR_PROPIOS",
    "TICKETS_ASIGNAR", "TICKETS_ASIGNAR_OTROS", "TICKETS_ELIMINAR",
    "COMENTARIOS_CREAR", "COMENTARIOS_ELIMINAR",
    "ROLES_VER", "ROLES_GESTIONAR",
    "ARTICULOS_VER", "ARTICULOS_VER_INTERNOS", "ARTICULOS_CREAR", "ARTICULOS_EDITAR", "ARTICULOS_ELIMINAR",
    "CAPACITACIONES_VER", "CAPACITACIONES_CREAR", "CAPACITACIONES_EDITAR", "CAPACITACIONES_ELIMINAR",
    "CAPACITACIONES_SOLICITAR",
    "SOLICITUDES_GESTIONAR",
    "INSCRIPCIONES_PROPIAS", "INSCRIPCIONES_GESTIONAR", "INSCRIPCIONES_VER",
    "ASISTENCIA_MARCAR",
    "REPORTES_VER"
  ];
  if (PERMISOS.length !== 49) throw new Error("El catálogo debe tener 49 permisos y tiene " + PERMISOS.length);

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "nombre", "descripcion", "alcance", "permisos", "activo", "esSistema"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        _class: { bsonType: "string" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        nombre: texto(50),
        descripcion: texto(500),
        alcance: { enum: ["GLOBAL", "EMPRESA"] },
        permisos: {
          bsonType: "array",
          minItems: 1,
          maxItems: PERMISOS.length,
          uniqueItems: true,
          items: { enum: PERMISOS }
        },
        activo: { bsonType: "bool" },
        esSistema: { bsonType: "bool" }
      }
    }
  };

  const opciones = { validationLevel: "strict", validationAction: "error" };
  try {
    if (helpdesk.getCollectionNames().includes(NOMBRE)) {
      helpdesk.runCommand(Object.assign({ collMod: NOMBRE, validator: validador }, opciones));
      print(NOMBRE + ": validador actualizado con collMod.");
    } else {
      helpdesk.createCollection(NOMBRE, Object.assign({ validator: validador }, opciones));
      print(NOMBRE + ": colección creada con validador.");
    }
  } catch (e) {
    if (e.code === 13) print("Sin permiso para collMod. El usuario necesita el rol dbAdmin sobre helpdesk.");
    throw e;
  }
})();
