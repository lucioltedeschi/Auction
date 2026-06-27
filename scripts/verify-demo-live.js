const path = require("path");
const sql = require("mssql");

require("dotenv").config({ path: path.join(__dirname, "..", ".env"), quiet: true });

async function main() {
  const pool = await new sql.ConnectionPool({
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    server: process.env.DB_SERVER,
    database: process.env.DB_NAME || "auction",
    port: Number(process.env.DB_PORT || 1433),
    options: { encrypt: true, trustServerCertificate: false },
  }).connect();
  const result = await pool.request().query(`
    SELECT a.identificador, a.ubicacion, a.estado, a.categoria, a.moneda,
           a.duracionItemMinutos, COUNT(DISTINCT ci.identificador) AS lotes,
           COUNT(DISTINCT CASE WHEN ci.vendido='no' THEN ci.identificador END) AS lotesAbiertos,
           COUNT(b.identificador) AS pujas
    FROM Auctions a
    LEFT JOIN Catalogs c ON c.subasta=a.identificador
    LEFT JOIN CatalogItems ci ON ci.catalogo=c.identificador
    LEFT JOIN Bids b ON b.item=ci.identificador
    WHERE a.ubicacion LIKE 'DEMO XL - %'
    GROUP BY a.identificador, a.ubicacion, a.estado, a.categoria, a.moneda, a.duracionItemMinutos
    ORDER BY a.identificador
  `);
  const nuevas = result.recordset.filter((auction) =>
    /Fotografía|Relojería|Objetos de Autor/.test(auction.ubicacion));
  if (nuevas.length !== 3 || nuevas.some((auction) => Number(auction.lotes) < 4 || Number(auction.pujas) < 12)) {
    throw new Error("El nuevo escenario de subastas y pujas no quedó completo");
  }
  console.log(JSON.stringify({ ok: true, nuevasSubastas: nuevas, totalSubastasDemo: result.recordset.length }));
  await pool.close();
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
