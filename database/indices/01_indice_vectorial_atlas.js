// =====================================================================
// 01_indice_vectorial_atlas.js · Índice de Atlas Vector Search (tarea 1.4)
//
// SOLO ATLAS. En el MongoDB local este índice no existe (contexto 6.3);
// el script lo detecta y termina sin hacer nada.
//
// Índice: vec_articulos_embedding sobre helpdesk.articulos
//   - embedding: 768 dimensiones, similitud cosine (Gemini gemini-embedding-001)
//   - filtros: modulo y visibilidad. El backend agrega visibilidad: "PUBLICO"
//     cuando el usuario no tiene ARTICULOS_VER_INTERNOS (contexto 6.4).
// Ocupa 1 de los 3 índices de búsqueda que permite el M0.
//
// Cómo se usa: pegar en la consola mongosh de Compass conectada a Atlas.
// Si createSearchIndex falla (permiso del usuario de base de datos o
// restricción del M0), el script imprime el JSON para crearlo a mano:
// Atlas > clúster helpdesk > Atlas Search > Create Search Index >
// Atlas Vector Search > JSON Editor > base helpdesk, colección articulos,
// nombre vec_articulos_embedding.
// =====================================================================

(() => {

const NOMBRE = "vec_articulos_embedding";
const definicion = {
  fields: [
    { type: "vector", path: "embedding", numDimensions: 768, similarity: "cosine" },
    { type: "filter", path: "modulo" },
    { type: "filter", path: "visibilidad" }
  ]
};

const base = db.getSiblingDB("helpdesk");
const setName = db.hello().setName || "";

if (!setName.startsWith("atlas-")) {
  print(`Replica set "${setName}": no es Atlas. El índice vectorial no se crea en local.`);
  return;
}

if (!base.getCollectionNames().includes("articulos")) {
  print("La colección articulos no existe. Corre primero validadores/00_todos_los_validadores.js");
  return;
}

const col = base.getCollection("articulos");

let existentes = [];
try {
  existentes = col.getSearchIndexes();
} catch (e) {
  print(`No se pudo listar los índices de búsqueda: ${e.message}`);
}

const actual = existentes.find(i => i.name === NOMBRE);

try {
  if (actual) {
    col.updateSearchIndex(NOMBRE, definicion);
    print(`Índice ${NOMBRE} ya existía: se envió la definición actual (Atlas lo reconstruye si cambió).`);
  } else {
    col.createSearchIndex(NOMBRE, "vectorSearch", definicion);
    print(`Índice ${NOMBRE} enviado. Atlas lo construye en segundo plano (alrededor de un minuto).`);
  }
  print("Para ver el estado: db.getSiblingDB('helpdesk').articulos.getSearchIndexes()");
  print("Debe llegar a status: 'READY' (en la interfaz de Atlas aparece como Active).");
} catch (e) {
  print(`createSearchIndex falló: código ${e.code} · ${e.message}`);
  print("Créalo desde la interfaz de Atlas con este JSON:\n");
  print(JSON.stringify(definicion, null, 2));
}

})();
