const fs = require("fs");
const path = require("path");
const sql = require("mssql");

require("dotenv").config({ path: path.join(__dirname, "..", ".env") });

const archivosDisponibles = [
  "CREAR_BASE.sql",
  "DATOS_DEMO_BASE.sql",
  "ENTREGA3_CORRECCIONES.sql",
  "ENTREGA3_DEMO_SUBASTAS.sql",
  "DATOS_DEMO_MULTA.sql",
];

const archivoSolicitado = process.argv[2];
if (archivoSolicitado && !archivosDisponibles.includes(archivoSolicitado)) {
  throw new Error(`Archivo no permitido: ${archivoSolicitado}`);
}
const archivos = archivoSolicitado ? [archivoSolicitado] : archivosDisponibles;

function variableRequerida(nombre) {
  const valor = process.env[nombre];
  if (!valor) throw new Error(`Falta ${nombre} en el archivo .env`);
  return valor;
}

function obtenerBatches(nombreArchivo) {
  const contenido = fs.readFileSync(path.join(__dirname, nombreArchivo), "utf8");
  const batches = contenido.split(/^\s*GO\s*;?\s*$/gim);

  return batches
    .map((batch) => batch.replace(/^\uFEFF/, "").trim())
    .filter(Boolean)
    .filter((batch, indice) => {
      if (/^USE\s+\[?auction\]?\s*;?$/i.test(batch)) return false;
      if (nombreArchivo === "CREAR_BASE.sql" && indice === 0 && /CREATE\s+DATABASE/i.test(batch)) {
        return false;
      }
      return true;
    });
}

async function ejecutar() {
  const config = {
    user: variableRequerida("DB_USER"),
    password: variableRequerida("DB_PASSWORD"),
    server: variableRequerida("DB_SERVER"),
    database: process.env.DB_NAME || "auction",
    port: Number(process.env.DB_PORT || 1433),
    options: {
      encrypt: true,
      trustServerCertificate: false,
    },
    connectionTimeout: 30000,
    requestTimeout: 120000,
  };

  const pool = await new sql.ConnectionPool(config).connect();
  console.log(`Conectado a ${config.server}/${config.database}`);

  try {
    for (const archivo of archivos) {
      const batches = obtenerBatches(archivo);
      console.log(`Ejecutando ${archivo} (${batches.length} bloques)...`);

      for (let i = 0; i < batches.length; i += 1) {
        try {
          await pool.request().batch(batches[i]);
        } catch (error) {
          throw new Error(`${archivo}, bloque ${i + 1}: ${error.message}`);
        }
      }
    }

    console.log("Base Azure preparada correctamente para Entrega 3.");
  } finally {
    await pool.close();
  }
}

ejecutar().catch((error) => {
  console.error(`No se pudo preparar Azure SQL: ${error.message}`);
  process.exitCode = 1;
});
