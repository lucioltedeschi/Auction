const { sql, poolPromise } = require("../backend/db");

const baseUrl = process.env.API_BASE_URL || "http://127.0.0.1:3000";
let saleId = null;
let fineId = null;
let initialNotificationId = 0;

async function api(route, options = {}) {
  const response = await fetch(`${baseUrl}${route}`, {
    ...options,
    headers: { Accept: "application/json", "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const raw = await response.text();
  return { status: response.status, body: raw ? JSON.parse(raw) : {} };
}

function assert(condition, message, detail) {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(detail)}`);
}

async function prepare() {
  const pool = await poolPromise;
  const source = await pool.request().query(`
    SELECT TOP 1 a.identificador AS auctionId, p.identificador AS productId, p.duenio,
      pm.identificador AS paymentMethodId
    FROM Auctions a
    INNER JOIN Catalogs c ON c.subasta=a.identificador
    INNER JOIN CatalogItems ci ON ci.catalogo=c.identificador
    INNER JOIN Products p ON p.identificador=ci.producto
    CROSS JOIN PaymentMethods pm
    WHERE a.moneda='pesos' AND pm.cliente=3 AND pm.verificado='si'
      AND pm.moneda='pesos' AND pm.tipo<>'cheque_certificado'
    ORDER BY a.identificador DESC
  `);
  assert(source.recordset.length === 1, "No hay datos base para la prueba", source.recordset);
  const item = source.recordset[0];
  const maxNotification = await pool.request().query("SELECT ISNULL(MAX(identificador),0) AS id FROM Notifications");
  initialNotificationId = Number(maxNotification.recordset[0].id);
  const inserted = await pool.request()
    .input("auctionId", sql.Int, item.auctionId)
    .input("ownerId", sql.Int, item.duenio)
    .input("productId", sql.Int, item.productId)
    .input("clientId", sql.Int, 3)
    .query(`
      INSERT INTO AuctionRecords (subasta,duenio,producto,cliente,importe,comision,costoEnvio,estadoPago,fechaVenta,retiroPersonal)
      OUTPUT INSERTED.identificador AS id
      VALUES (@auctionId,@ownerId,@productId,@clientId,1000,100,0,'pendiente',DATEADD(HOUR,-73,${"DATEADD(HOUR,-3,SYSUTCDATETIME())"}),'no')
    `);
  saleId = Number(inserted.recordset[0].id);
  return item.paymentMethodId;
}

async function cleanup() {
  const pool = await poolPromise;
  if (fineId) await pool.request().input("id", sql.Int, fineId).query("DELETE FROM Fines WHERE identificador=@id");
  if (saleId) await pool.request().input("id", sql.Int, saleId).query("DELETE FROM AuctionRecords WHERE identificador=@id");
  await pool.request().input("first", sql.Int, initialNotificationId)
    .query("DELETE FROM Notifications WHERE identificador>@first AND cliente=3 AND titulo IN ('Multa pendiente por impago','Multa regularizada')");
}

async function run() {
  const paymentMethodId = await prepare();
  const admin = await api("/api/auth/login", { method: "POST", body: JSON.stringify({ documento: "20000111", clave: "1234" }) });
  const client = await api("/api/auth/login", { method: "POST", body: JSON.stringify({ documento: "30123456", clave: "1234" }) });
  assert(admin.status === 200 && client.status === 200, "No se pudo iniciar sesion", { admin, client });
  const adminAuth = { Authorization: `Bearer ${admin.body.token}` };
  const clientAuth = { Authorization: `Bearer ${client.body.token}` };

  const options = await api("/api/admin/action-options", { headers: adminAuth });
  const debt = options.body.impagos?.find((item) => Number(item.ventaId) === saleId);
  assert(debt && Number(debt.importeOfertado) === 1000 && Number(debt.multaSugerida) === 100,
    "La multa sugerida no es el 10% de la oferta", debt);

  const create = await api("/api/admin/fines", {
    method: "POST", headers: adminAuth, body: JSON.stringify({ ventaId: saleId, monto: 999999 }),
  });
  assert(create.status === 201 && Number(create.body.multa.monto) === 100,
    "El backend no calculo la multa reglamentaria", create.body);
  fineId = Number(create.body.multa.id);

  const fines = await api("/api/clients/3/fines", { headers: clientAuth });
  const fine = fines.body.find((item) => Number(item.id) === fineId);
  assert(fine && Number(fine.importeOfertado) === 1000 && Number(fine.monto) === 100,
    "El cliente no ve el detalle de la multa", fine);

  const pay = await api(`/api/fines/${fineId}/pay`, {
    method: "POST", headers: clientAuth, body: JSON.stringify({ medioPagoId: paymentMethodId }),
  });
  assert(pay.status === 200 && pay.body.multa.pagada === "si", "No se pudo pagar con un medio verificado", pay.body);

  console.log(JSON.stringify({ ok: true, saleId, fineId, offered: 1000, fine: 100, paidWith: paymentMethodId }, null, 2));
}

run().catch((error) => { console.error(error.stack || error.message); process.exitCode = 1; })
  .finally(async () => {
    try { await cleanup(); } catch (error) { console.error("Cleanup:", error.message); process.exitCode = 1; }
    try { const pool = await poolPromise; await pool.close(); } catch (ignored) {}
  });
