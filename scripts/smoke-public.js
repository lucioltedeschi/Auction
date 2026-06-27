const BASE_URL = process.env.API_BASE_URL || "https://auct-io-api.onrender.com";

async function request(path, options = {}, expected = 200) {
  const response = await fetch(BASE_URL + path, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {}),
    },
  });
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  if (response.status !== expected) {
    throw new Error(`${options.method || "GET"} ${path}: esperado ${expected}, recibido ${response.status} - ${text}`);
  }
  console.log(`OK ${response.status} ${options.method || "GET"} ${path}`);
  return body;
}

async function login(documento, clave, expected = 200) {
  return request("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ documento, clave }),
  }, expected);
}

async function run() {
  await request("/api/health");
  await request("/api/test");
  await login("30123456", "incorrecta", 401);
  await login("40000111", "1234", 403);

  const cliente = await login("30123456", "1234");
  const clienteId = cliente.usuario.id;
  const clienteHeaders = { Authorization: `Bearer ${cliente.token}` };

  await request(`/api/users/${clienteId}`, { headers: clienteHeaders });
  const subastas = await request(`/api/clients/${clienteId}/auctions`, { headers: clienteHeaders });
  await request(`/api/clients/${clienteId}/payment-methods`, { headers: clienteHeaders });
  await request(`/api/clients/${clienteId}/history`, { headers: clienteHeaders });
  await request(`/api/clients/${clienteId}/purchases`, { headers: clienteHeaders });
  await request(`/api/clients/${clienteId}/fines`, { headers: clienteHeaders });
  await request(`/api/clients/${clienteId}/notifications`, { headers: clienteHeaders });
  await request(`/api/clients/${clienteId}/products`, { headers: clienteHeaders });

  if (!Array.isArray(subastas) || subastas.length === 0) throw new Error("No hay subastas demo disponibles");
  const auctionId = subastas[0].id;
  await request(`/api/auctions/${auctionId}`, { headers: clienteHeaders });
  await request(`/api/auctions/${auctionId}/catalog`, { headers: clienteHeaders });

  const admin = await login("20000111", "1234");
  const adminHeaders = { Authorization: `Bearer ${admin.token}` };
  await request("/api/admin/users/pending", { headers: adminHeaders });
  await request("/api/admin/payment-methods/pending", { headers: adminHeaders });
  await request("/api/admin/products/pending", { headers: adminHeaders });

  console.log("SMOKE PUBLICO COMPLETO");
}

run().catch((error) => {
  console.error(`SMOKE FALLIDO: ${error.message}`);
  process.exitCode = 1;
});
