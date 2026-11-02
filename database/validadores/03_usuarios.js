// =====================================================================
// Validador $jsonSchema de la colección usuarios (tarea 1.3)
// Base de datos: helpdesk · Fuente: modelo/MODELO_DATOS_1.2.md, sección 3
// Ejecución: pegar en la consola mongosh de Compass, o
//   mongosh "<cadena>" --file database/validadores/03_usuarios.js
// Crea la colección o reemplaza su validador con collMod.
//
// email solo acepta minúsculas: el backend lo normaliza antes de guardar.
// passwordHash solo acepta el formato BCrypt de 60 caracteres que produce
// BCryptPasswordEncoder ($2a$10$...). Una contraseña en texto plano no entra.
//
// El backend revisa: que todos los roles tengan el mismo alcance, que
// empresaId sea null con roles GLOBAL y no null con roles EMPRESA, y el
// límite maxUsuarios del plan.
// =====================================================================

(function () {
  const helpdesk = db.getSiblingDB("helpdesk");
  const NOMBRE = "usuarios";

  const texto = (max) => ({ bsonType: "string", minLength: 1, maxLength: max, pattern: "\\S" });

  const validador = {
    $jsonSchema: {
      bsonType: "object",
      title: NOMBRE,
      required: ["schemaVersion", "fechaCreacion", "fechaActualizacion",
        "nombre", "email", "passwordHash", "rolIds", "empresaId", "activo"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        schemaVersion: { bsonType: "int", minimum: 1 },
        fechaCreacion: { bsonType: "date" },
        fechaActualizacion: { bsonType: "date" },

        nombre: texto(150),
        email: { bsonType: "string", maxLength: 254, pattern: "^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$" },
        passwordHash: { bsonType: "string", pattern: "^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$" },
        rolIds: {
          bsonType: "array",
          minItems: 1,
          maxItems: 10,
          uniqueItems: true,
          items: { bsonType: "objectId" }
        },
        empresaId: { bsonType: ["objectId", "null"] },
        activo: { bsonType: "bool" }
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
