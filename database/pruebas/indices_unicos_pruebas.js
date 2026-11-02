// =====================================================================
// indices_unicos_pruebas.js · Prueba de los 7 índices únicos (tarea 1.4)
//
// Copia la definición real de cada índice único de helpdesk a la base
// temporal helpdesk_pruebas (sin validador, para probar solo el índice) y
// hace tres inserciones por índice:
//   1. un documento                      -> se acepta
//   2. el mismo valor de clave otra vez  -> se rechaza con E11000
//   3. un valor distinto                 -> se acepta
// Al final borra helpdesk_pruebas. No toca helpdesk.
//
// Cómo se usa: pegar en la consola mongosh de Compass después de
// indices/00_indices.js. Debe imprimir 21/21.
// =====================================================================

(() => {

const origen = db.getSiblingDB("helpdesk");
const pruebas = db.getSiblingDB("helpdesk_pruebas");
pruebas.dropDatabase();

// Valor de relleno según el nombre del campo.
const valor = (campo, n) => campo.endsWith("Id") ? new ObjectId() : `valor-${campo}-${n}`;

let pasaron = 0, total = 0;
const registrar = (ok, texto) => { total++; if (ok) pasaron++; print(`  ${ok ? "✔" : "✘"} ${texto}`); };

const colecciones = ["empresas", "roles", "usuarios", "planes", "membresias", "inscripciones", "refreshTokens"];

for (const nombreCol of colecciones) {
  const unicos = origen.getCollection(nombreCol).getIndexes().filter(i => i.unique);
  if (unicos.length === 0) {
    registrar(false, `${nombreCol}: no tiene índice único en helpdesk (¿corriste 00_indices.js?)`);
    continue;
  }

  for (const ix of unicos) {
    const col = pruebas.getCollection(nombreCol);
    col.createIndex(ix.key, { name: ix.name, unique: true });

    const campos = Object.keys(ix.key);
    const doc1 = {}; campos.forEach(c => doc1[c] = valor(c, 1));
    const doc2 = Object.assign({}, doc1);           // misma clave
    delete doc2._id;                                // que el choque sea por el índice y no por _id
    const doc3 = {}; campos.forEach(c => doc3[c] = valor(c, 3));

    print(`\n${nombreCol}.${ix.name}  ${JSON.stringify(ix.key)}`);

    try { col.insertOne(doc1); registrar(true, "primer documento aceptado"); }
    catch (e) { registrar(false, `primer documento rechazado: ${e.message}`); }

    try { col.insertOne(doc2); registrar(false, "duplicado ACEPTADO (el índice no protege)"); }
    catch (e) { registrar(e.code === 11000, `duplicado rechazado (código ${e.code})`); }

    try { col.insertOne(doc3); registrar(true, "valor distinto aceptado"); }
    catch (e) { registrar(false, `valor distinto rechazado: ${e.message}`); }
  }
}

pruebas.dropDatabase();
print(`\n${pasaron}/${total}`);

})();
