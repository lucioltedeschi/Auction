const baseUrl = (process.env.API_BASE_URL || "http://localhost:3100").replace(/\/$/, "");

async function request(path, options = {}, expected) {
  const response = await fetch(baseUrl + path, options);
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : {}; } catch { body = { raw: text }; }
  if (expected && !expected.includes(response.status)) {
    throw new Error(`${options.method || "GET"} ${path}: ${response.status} ${JSON.stringify(body)}`);
  }
  return { status: response.status, body };
}

async function run() {
  const login = await request("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ documento: "20000111", clave: "1234" }),
  }, [200]);
  const token = login.body.token;
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

  const before = await request("/api/admin/auctions", { headers }, [200]);
  console.log(`LISTADO OK: ${before.body.length} subastas`);

  const marker = `TEST ABM ${Date.now()}`;
  const created = await request("/api/admin/auctions", {
    method: "POST", headers,
    body: JSON.stringify({
      fecha: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
      hora: "19:30", estado: "programada", ubicacion: marker,
      capacidadAsistentes: 80, tieneDeposito: "si", seguridadPropia: "si",
      categoria: "comun", moneda: "pesos", duracionItemMinutos: 90,
    }),
  }, [201]);
  const id = created.body.id;
  console.log(`ALTA OK: #${id}`);

  await request(`/api/admin/auctions/${id}`, {
    method: "PATCH", headers,
    body: JSON.stringify({
      fecha: new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10),
      hora: "20:15", estado: "abierta", ubicacion: `${marker} EDITADA`,
      capacidadAsistentes: 100, tieneDeposito: "no", seguridadPropia: "si",
      categoria: "plata", moneda: "dolares", duracionItemMinutos: 120,
    }),
  }, [200]);
  console.log(`MODIFICACIÓN OK: #${id}`);

  await request(`/api/admin/auctions/${id}`, { method: "DELETE", headers }, [200]);
  console.log(`BAJA SEGURA OK: #${id}`);

  const after = await request("/api/admin/auctions", { headers }, [200]);
  if (after.body.some((auction) => auction.id === id)) throw new Error("La baja no se reflejó en el listado");
  console.log("ABM ADMINISTRATIVO COMPLETO");
}

run().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
