const { poolPromise, sql } = require("../backend/db");

const BASE_URL = process.env.API_BASE_URL || "http://127.0.0.1:3001";
const MARK = `TEST-CIERRE-${Date.now()}`;

async function api(path, options = {}) {
  const response = await fetch(BASE_URL + path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : {}; } catch { body = { raw: text }; }
  if (!response.ok) throw new Error(`${options.method || "GET"} ${path}: ${response.status} ${body.error || text}`);
  return body;
}

async function login(documento) {
  return api("/api/auth/login", { method: "POST", body: JSON.stringify({ documento, clave: "1234" }) });
}

async function cleanup(pool, auctionId, productIds) {
  if (!auctionId) return;
  const request = pool.request().input("auction", sql.Int, auctionId).input("mark", sql.VarChar(150), `%${MARK}%`);
  await request.query(`
    DELETE FROM Notifications WHERE mensaje LIKE @mark;
    DELETE FROM AuctionRecords WHERE subasta=@auction;
    DELETE b FROM Bids b INNER JOIN CatalogItems ci ON ci.identificador=b.item INNER JOIN Catalogs c ON c.identificador=ci.catalogo WHERE c.subasta=@auction;
    DELETE FROM Attendees WHERE subasta=@auction;
    DELETE ci FROM CatalogItems ci INNER JOIN Catalogs c ON c.identificador=ci.catalogo WHERE c.subasta=@auction;
    DELETE FROM Catalogs WHERE subasta=@auction;
    DELETE FROM Auctions WHERE identificador=@auction;
  `);
  for (const id of productIds || []) {
    await pool.request().input("product", sql.Int, id).query("DELETE FROM Photos WHERE producto=@product; DELETE FROM Products WHERE identificador=@product;");
  }
}

(async () => {
  const pool = await poolPromise;
  let auctionId;
  const productIds = [];
  try {
    const refs = await pool.request().query(`
      SELECT TOP 1 identificador AS auctioneer FROM Auctioneers ORDER BY identificador;
      SELECT TOP 1 identificador AS employee FROM Employees ORDER BY identificador;
      SELECT TOP 1 c.identificador AS owner FROM Clients c INNER JOIN Owners o ON o.identificador=c.identificador WHERE c.admitido='si' AND c.identificador<>9000007 ORDER BY c.identificador;
    `);
    const auctioneer = refs.recordsets[0][0].auctioneer;
    const employee = refs.recordsets[1][0].employee;
    const owner = refs.recordsets[2][0].owner;
    const created = await pool.request().input("auctioneer", sql.Int, auctioneer).input("mark", sql.VarChar(350), MARK).query(`
      INSERT INTO Auctions (fecha,hora,estado,subastador,ubicacion,capacidadAsistentes,tieneDeposito,seguridadPropia,categoria,moneda,duracionItemMinutos)
      OUTPUT INSERTED.identificador
      VALUES (CAST(DATEADD(HOUR,-3,SYSUTCDATETIME()) AS date),CAST(DATEADD(MINUTE,30,DATEADD(HOUR,-3,SYSUTCDATETIME())) AS time),'en_curso',@auctioneer,@mark,20,'si','si','comun','pesos',60)
    `);
    auctionId = created.recordset[0].identificador;
    const catalog = await pool.request().input("auction", sql.Int, auctionId).input("employee", sql.Int, employee)
      .query("INSERT INTO Catalogs (descripcion,subasta,responsable) OUTPUT INSERTED.identificador VALUES ('Prueba cierre integral',@auction,@employee)");
    const catalogId = catalog.recordset[0].identificador;
    const itemIds = [];
    for (let index = 1; index <= 2; index += 1) {
      const product = await pool.request().input("owner", sql.Int, owner).input("name", sql.VarChar(500), `${MARK}-LOTE-${index}`).query(`
        INSERT INTO Products (fecha,disponible,descripcionCatalogo,descripcionCompleta,declaracionPropiedad,origenLicito,estadoAprobacion,duenio,precioBasePropuesto,comisionPropuesta)
        OUTPUT INSERTED.identificador VALUES (CAST(DATEADD(HOUR,-3,SYSUTCDATETIME()) AS date),'si',@name,@name,'si','si','incluido_subasta',@owner,10000,0.10)
      `);
      productIds.push(product.recordset[0].identificador);
      const item = await pool.request().input("catalog", sql.Int, catalogId).input("product", sql.Int, product.recordset[0].identificador).query(`
        INSERT INTO CatalogItems (catalogo,producto,precioBase,comision,subastado,vendido)
        OUTPUT INSERTED.identificador VALUES (@catalog,@product,10000,0.10,'no','no')
      `);
      itemIds.push(item.recordset[0].identificador);
    }

    const user = await login("30123456");
    const admin = await login("20000111");
    const userId = Number(user.usuario.id);
    await api("/api/bids", {
      method: "POST",
      headers: { Authorization: `Bearer ${user.token}` },
      body: JSON.stringify({ clienteId: userId, subastaId: auctionId, itemId: itemIds[0], importe: 10100 }),
    });
    await api(`/api/admin/auctions/${auctionId}/items/${itemIds[1]}/close`, {
      method: "POST", headers: { Authorization: `Bearer ${admin.token}` }, body: "{}",
    });
    await api(`/api/admin/auctions/${auctionId}/items/${itemIds[0]}/close`, {
      method: "POST", headers: { Authorization: `Bearer ${admin.token}` }, body: "{}",
    });

    const verified = await pool.request().input("auction", sql.Int, auctionId).query(`
      SELECT estado FROM Auctions WHERE identificador=@auction;
      SELECT COUNT(*) AS abiertos FROM CatalogItems ci INNER JOIN Catalogs c ON c.identificador=ci.catalogo WHERE c.subasta=@auction AND ci.vendido='no';
      SELECT COUNT(*) AS ventas, SUM(CASE WHEN cliente=9000007 THEN 1 ELSE 0 END) AS comprasEmpresa FROM AuctionRecords WHERE subasta=@auction;
    `);
    const state = verified.recordsets[0][0].estado;
    const open = Number(verified.recordsets[1][0].abiertos);
    const sales = Number(verified.recordsets[2][0].ventas);
    const companySales = Number(verified.recordsets[2][0].comprasEmpresa);
    if (state !== "cerrada" || open !== 0 || sales !== 2 || companySales !== 1) {
      throw new Error(`Resultado inconsistente: estado=${state}, abiertos=${open}, ventas=${sales}, empresa=${companySales}`);
    }

    await api(`/api/clients/${userId}/active-auction`, {
      method: "POST", headers: { Authorization: `Bearer ${user.token}` }, body: JSON.stringify({ auctionId: 17 }),
    });
    await api(`/api/clients/${userId}/active-auction/release`, {
      method: "POST", headers: { Authorization: `Bearer ${user.token}` }, body: JSON.stringify({ auctionId: 17 }),
    });
    console.log(JSON.stringify({ ok: true, auctionClosed: state, sales, companyPurchaseAtBase: companySales, sessionReleasedAfterBidAndWin: true }, null, 2));
  } finally {
    await cleanup(pool, auctionId, productIds).catch((error) => console.error("Cleanup:", error.message));
    await pool.close();
  }
})().catch((error) => {
  console.error("PRUEBA DE CIERRE FALLIDA:", error.message);
  process.exit(1);
});
