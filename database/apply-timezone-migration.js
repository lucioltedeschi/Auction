const fs = require("fs");
const path = require("path");
const { poolPromise } = require("../backend/db");

(async () => {
  const pool = await poolPromise;
  const migration = fs.readFileSync(path.join(__dirname, "MIGRACION_ZONA_HORARIA_ARGENTINA.sql"), "utf8");
  const result = await pool.request().batch(migration);
  const verification = result.recordsets[result.recordsets.length - 1]?.[0] || {};
  console.log("Migracion horaria aplicada:", JSON.stringify(verification));
  await pool.close();
})().catch((error) => {
  console.error("No se pudo aplicar la migracion horaria:", error.message);
  process.exit(1);
});
