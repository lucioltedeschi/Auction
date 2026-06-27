const path = require("path");
const sql = require("mssql");

require("dotenv").config({ path: path.join(__dirname, "..", ".env"), quiet: true });

async function verificar() {
  const pool = await new sql.ConnectionPool({
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    server: process.env.DB_SERVER,
    database: process.env.DB_NAME,
    port: Number(process.env.DB_PORT || 1433),
    options: { encrypt: true, trustServerCertificate: false },
    connectionTimeout: 30000,
    requestTimeout: 60000,
  }).connect();

  try {
    const tablasResult = await pool.request().query("SELECT COUNT(*) AS cantidad FROM sys.tables");
    const resumen = { tablas: tablasResult.recordset[0].cantidad };
    const entidades = {
      usuarios: "Users",
      subastas: "Auctions",
      productos: "Products",
      lotes: "CatalogItems",
    };

    for (const [etiqueta, tabla] of Object.entries(entidades)) {
      const existe = await pool.request().query(`SELECT OBJECT_ID('dbo.${tabla}', 'U') AS id`);
      if (!existe.recordset[0].id) {
        resumen[etiqueta] = -1;
        continue;
      }
      const cantidad = await pool.request().query(`SELECT COUNT(*) AS cantidad FROM dbo.${tabla}`);
      resumen[etiqueta] = cantidad.recordset[0].cantidad;
    }

    console.log(JSON.stringify(resumen));
  } finally {
    await pool.close();
  }
}

verificar().catch((error) => {
  console.error(`Verificacion fallida: ${error.message}`);
  process.exitCode = 1;
});
