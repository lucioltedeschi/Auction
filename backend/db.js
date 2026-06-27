const path = require("path");
require("dotenv").config({ path: path.join(__dirname, "..", ".env") });
const sql = require("mssql");

function envBool(name, defaultValue) {
    const raw = process.env[name];
    if (raw === undefined || raw === null || raw === "") return defaultValue;
    return ["1", "true", "si", "yes"].includes(String(raw).toLowerCase());
}

const config = {
    user: process.env.DB_USER || "sa",
    password: process.env.DB_PASSWORD || "1234",
    server: process.env.DB_SERVER || "localhost",
    database: process.env.DB_NAME || "auction",
    options: {
        encrypt: envBool("DB_ENCRYPT", false),
        trustServerCertificate: envBool("DB_TRUST_CERT", true)
    },
    port: Number(process.env.DB_PORT || 1433)
};

let activePool = null;
let connectionAttempt = null;

async function getPool() {
    if (activePool && activePool.connected) return activePool;
    if (connectionAttempt) return connectionAttempt;

    const candidate = new sql.ConnectionPool(config);
    connectionAttempt = candidate.connect()
        .then(pool => {
            activePool = pool;
            activePool.on("error", err => {
                console.error("Conexion SQL interrumpida:", err.message);
                activePool = null;
            });
            console.log(`Conectado a SQL Server ${config.server}:${config.port}/${config.database}`);
            return activePool;
        })
        .catch(async err => {
            activePool = null;
            try { await candidate.close(); } catch (ignored) { }
            console.error("Error de conexion SQL:", err.message);
            throw err;
        })
        .finally(() => {
            connectionAttempt = null;
        });

    return connectionAttempt;
}

// Thenable compatible con todos los `await poolPromise` existentes. Cada
// request reintenta la conexion si Azure estaba pausado o si la red se corto.
const poolPromise = {
    then: (resolve, reject) => getPool().then(resolve, reject),
    catch: reject => getPool().catch(reject),
    finally: callback => getPool().finally(callback)
};

module.exports = { sql, poolPromise };
