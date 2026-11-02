// =====================================================================
// Pruebas del validador de tickets (tarea 1.3)
//
// Requisito: haber corrido antes database/validadores/07_tickets.js.
// El script copia el validador de helpdesk.tickets a una base temporal
// helpdesk_pruebas, inserta 17 documentos y al final borra esa base.
// No toca los datos de helpdesk.
//
// Resultado esperado: 6 inserciones aceptadas, 11 rechazadas, 17/17 OK.
// =====================================================================

(function () {
  const origen = db.getSiblingDB("helpdesk").getCollectionInfos({ name: "tickets" })[0];
  if (!origen) { print("Falta helpdesk.tickets. Corre primero 07_tickets.js."); return; }

  const pruebas = db.getSiblingDB("helpdesk_pruebas");
  pruebas.dropDatabase();
  pruebas.createCollection("tickets", {
    validator: origen.options.validator,
    validationLevel: "strict",
    validationAction: "error"
  });
  const col = pruebas.getCollection("tickets");

  const ahora = new Date();
  const usuario = new ObjectId();

  // Documento base válido (sugerencia). Cada caso lo modifica.
  const base = () => ({
    schemaVersion: 1,
    fechaCreacion: ahora,
    fechaActualizacion: ahora,
    empresaId: new ObjectId(),
    creadorId: usuario,
    solicitanteId: usuario,
    agenteId: null,
    modulo: "VENTAS_ONLINE",
    tipo: "SUGERENCIA",
    titulo: "Prueba de validador",
    descripcion: "Documento de prueba",
    estado: "ABIERTO",
    historialEstados: [{ estado: "ABIERTO", fecha: ahora, usuarioId: usuario }],
    fechaResolucion: null,
    fechaLimiteRespuesta: new Date(ahora.getTime() + 24 * 3600 * 1000),
    fechaPrimeraAsignacion: null
  });

  const incidente = (modulo, detalle) =>
    Object.assign(base(), { tipo: "INCIDENTE", modulo: modulo, detalle: detalle });

  const detalleVentasOnline = {
    pasarelaPagoId: "PAYU-7781",
    urlError: "https://tienda.cliente.co/checkout",
    navegador: "Chrome 141"
  };

  const casos = [
    // ---- Deben aceptarse ----
    ["1 Incidente VENTAS_ONLINE con logApi", true,
      incidente("VENTAS_ONLINE", Object.assign({ logApi: "HTTP 502 en /api/pagos" }, detalleVentasOnline))],
    ["2 Incidente PRODUCCION con temperatura", true,
      incidente("PRODUCCION", { maquinariaId: "MEZ-02", codigoLote: "L-2026-118", faseProceso: "MEZCLADO", temperaturaSensor: Double(78) })],
    ["3 Incidente INVENTARIOS solo obligatorios", true,
      incidente("INVENTARIOS", { codigoBodega: "B01", codigoProducto: "P-3301", tipoOperacion: "ALERTA_STOCK" })],
    ["4 Incidente COMPRAS con monto Decimal128", true,
      incidente("COMPRAS", { nitProveedor: "900123456-1", etapa: "CUENTA_POR_PAGAR", montoInvolucrado: NumberDecimal("1250000.50") })],
    ["5 Incidente VENTAS_EN_SITIO", true,
      incidente("VENTAS_EN_SITIO", { cajaId: "CAJA-3", turnoId: "T-0211", dispositivo: "DATAFONO", medioPago: "TARJETA" })],
    ["6 Sugerencia sin detalle", true, base()],

    // ---- Deben rechazarse ----
    ["7 Sugerencia con detalle", false,
      Object.assign(base(), { detalle: detalleVentasOnline })],
    ["8 Incidente sin detalle", false,
      Object.assign(base(), { tipo: "INCIDENTE" })],
    ["9 Módulo PRODUCCION con detalle de VENTAS_ONLINE", false,
      incidente("PRODUCCION", detalleVentasOnline)],
    ["10 Detalle con un campo que no existe", false,
      incidente("VENTAS_ONLINE", Object.assign({ sistemaOperativo: "Windows" }, detalleVentasOnline))],
    ["11 faseProceso fuera de la lista", false,
      incidente("PRODUCCION", { maquinariaId: "MEZ-02", codigoLote: "L-1", faseProceso: "HORNEADO" })],
    ["12 Estado inválido", false,
      Object.assign(base(), { estado: "PAUSADO" })],
    ["13 Título de 151 caracteres", false,
      Object.assign(base(), { titulo: "x".repeat(151) })],
    ["14 historialEstados vacío", false,
      Object.assign(base(), { historialEstados: [] })],
    ["15 Campo desconocido en la raíz", false,
      Object.assign(base(), { prioridad: "ALTA" })],
    ["16 temperaturaSensor como texto", false,
      incidente("PRODUCCION", { maquinariaId: "MEZ-02", codigoLote: "L-1", faseProceso: "EMPAQUE", temperaturaSensor: "78" })],
    ["17 Sin agenteId (debe venir null explícito)", false,
      (() => { const d = base(); delete d.agenteId; return d; })()]
  ];

  let correctos = 0;
  casos.forEach(([nombre, debeAceptarse, doc]) => {
    let aceptado = true;
    let motivo = "";
    try {
      col.insertOne(doc);
    } catch (e) {
      aceptado = false;
      motivo = e.code === 121 ? "DocumentValidationFailure" : (e.code + " " + e.message);
    }
    const ok = aceptado === debeAceptarse;
    if (ok) correctos++;
    print((ok ? "OK    " : "FALLA ") + nombre + " · " + (aceptado ? "aceptado" : "rechazado") + (motivo ? " (" + motivo + ")" : ""));
  });

  print("\n" + correctos + "/" + casos.length + " casos con el resultado esperado.");
  pruebas.dropDatabase();
})();
