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

const poolPromise = new sql.ConnectionPool(config)
    .connect()
    .then(pool => {
        console.log(`Conectado a SQL Server ${config.server}:${config.port}/${config.database}`);
        return pool;
    })
    .catch(err => console.log("Error de conexion SQL:", err));

module.exports = { sql, poolPromise };
