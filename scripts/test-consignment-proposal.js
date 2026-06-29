const fs = require("fs");
const path = require("path");
const { sql, poolPromise } = require("../backend/db");

const baseUrl = process.env.API_BASE_URL || "http://127.0.0.1:3000";
const marker = `REGRESION CONSIGNACION ${Date.now()}`;
let productId = null;
let ownerId = null;
let insurancePolicy = null;

async function api(route, options = {}) {
  const response = await fetch(`${baseUrl}${route}`, {
    ...options,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  const raw = await response.text();
  const body = raw ? JSON.parse(raw) : {};
  return { status: response.status, body };
}

function assert(condition, message, detail) {
  if (!condition) {
    throw new Error(`${message}${detail ? `: ${JSON.stringify(detail)}` : ""}`);
  }
}

async function cleanup() {
  if (!productId) return;
  const pool = await poolPromise;
  await pool.request()
    .input("productId", sql.Int, productId)
    .input("ownerId", sql.Int, ownerId)
    .input("marker", sql.VarChar(200), `%${marker}%`)
    .query(`
      DELETE FROM Photos WHERE producto = @productId;
      DELETE FROM Products WHERE identificador = @productId;
      DELETE FROM Insurances WHERE nroPoliza = '${insurancePolicy || "__none__"}';
      DELETE FROM Notifications WHERE cliente = @ownerId AND mensaje LIKE @marker;
    `);
}

async function run() {
  const adminLogin = await api("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ documento: "20000111", clave: "1234" }),
  });
  assert(adminLogin.status === 200, "No se pudo iniciar sesion como administrador", adminLogin.body);

  const clientLogin = await api("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ documento: "30123456", clave: "1234" }),
  });
  assert(clientLogin.status === 200, "No se pudo iniciar sesion como cliente", clientLogin.body);
  ownerId = Number(clientLogin.body.usuario.id);

  const photo = fs.readFileSync(path.join(__dirname, "..", "database", "demo-assets", "arte-showroom.jpg"))
    .toString("base64");
  const create = await api("/api/products", {
    method: "POST",
    body: JSON.stringify({
      duenio: ownerId,
      descripcionCatalogo: marker,
      descripcionCompleta: "Pieza aislada para validar el circuito cliente, administrador y propuesta.",
      historia: "Caso de regresion descartable.",
      artistaDiseniador: "Equipo QA",
      precioBaseSugerido: 1200,
      fotos: [photo, photo, photo, photo, photo, photo],
      declaracionPropiedad: "si",
      origenLicito: "si",
    }),
  });
  assert(create.status === 202, "No se pudo crear la consignacion de prueba", create.body);
  productId = Number(create.body.producto.identificador);

  const auth = { Authorization: `Bearer ${adminLogin.body.token}` };
  const gallery = await api(`/api/admin/products/${productId}/photos`, { headers: auth });
  assert(gallery.status === 200, "La galeria administrativa no respondio", gallery.body);
  assert(Array.isArray(gallery.body) && gallery.body.length === 6,
    "La galeria administrativa no devolvio las seis fotos", gallery.body);

  const invalidInsurance = await api(`/api/admin/products/${productId}/review`, {
    method: "PATCH",
    headers: auth,
    body: JSON.stringify({
      estadoAprobacion: "propuesta_enviada",
      precioBase: 1500,
      comision: 150,
      seguro: "Una descripcion no es un numero de poliza y antes rompia Azure SQL",
    }),
  });
  assert(invalidInsurance.status === 400,
    "Una poliza descriptiva debe rechazarse antes de llegar a Azure SQL", invalidInsurance.body);

  const review = await api(`/api/admin/products/${productId}/review`, {
    method: "PATCH",
    headers: auth,
    body: JSON.stringify({
      estadoAprobacion: "propuesta_enviada",
      precioBase: 1500,
      comision: 150,
      condicionesPropuestas: "Base y comision sujetas a aceptacion del consignante.",
      ubicacionDeposito: "Deposito interno de prueba",
    }),
  });
  assert(review.status === 200, "No se pudo enviar la propuesta al cliente", review.body);
  assert(review.body.estadoAprobacion === "propuesta_enviada", "Estado de propuesta incorrecto", review.body);
  insurancePolicy = review.body.seguro?.nroPoliza || null;
  assert(insurancePolicy && Number(review.body.seguro.importe) === 1500,
    "La propuesta no genero la poliza reglamentaria", review.body);

  const clientProducts = await api(`/api/clients/${ownerId}/products`);
  const clientProduct = Array.isArray(clientProducts.body)
    ? clientProducts.body.find((item) => Number(item.id) === productId) : null;
  assert(clientProducts.status === 200 && clientProduct,
    "El cliente no recibio su consignacion", clientProducts.body);
  assert(clientProduct.estadoAprobacion === "propuesta_enviada"
      && Number(clientProduct.precioBasePropuesto) === 1500
      && Number(clientProduct.comisionPropuesta) === 150
      && clientProduct.seguro === insurancePolicy
      && Number(clientProduct.seguroImporte) === 1500,
    "La propuesta no llego completa al cliente", clientProduct);

  const accept = await api(`/api/products/${productId}/proposal-response`, {
    method: "POST",
    body: JSON.stringify({ duenio: ownerId, decision: "aceptar" }),
  });
  assert(accept.status === 200 && accept.body.estadoAprobacion === "aceptado_usuario",
    "El cliente no pudo aceptar la propuesta", accept.body);

  console.log(JSON.stringify({
    ok: true,
    api: baseUrl,
    productId,
    photosVisibleToAdmin: gallery.body.length,
    proposalDelivered: true,
    insurancePolicy,
    clientAccepted: true,
  }, null, 2));
}

run()
  .catch((error) => {
    console.error(error.stack || error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    try {
      await cleanup();
    } catch (error) {
      console.error("No se pudo limpiar la consignacion de prueba:", error.message);
      process.exitCode = 1;
    }
    try {
      const pool = await poolPromise;
      await pool.close();
    } catch (ignored) {}
  });
