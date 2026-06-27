const baseUrl = process.env.API_BASE_URL || "https://auct-io-api.onrender.com";

async function json(url, options = {}) {
  const response = await fetch(url, options);
  const body = await response.json().catch(() => ({}));
  return { response, body };
}

async function main() {
  const loginResult = await json(`${baseUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ documento: "51000001", clave: "1234" }),
  });
  if (!loginResult.response.ok) throw new Error("No se pudo iniciar sesión para la prueba");
  const { token, usuario } = loginResult.body;
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

  let prueba = null;
  for (const auctionId of [8, 5, 7]) {
    const [live, catalog] = await Promise.all([
      json(`${baseUrl}/api/auctions/${auctionId}/live-state`, { headers }),
      json(`${baseUrl}/api/auctions/${auctionId}/catalog?clientId=${usuario.id}`, { headers }),
    ]);
    if (!live.response.ok || !Array.isArray(catalog.body)) continue;
    const activeId = Number(live.body.itemActual?.itemId || 0);
    const future = catalog.body.find((item) => item.vendido !== "si" && Number(item.itemId) !== activeId);
    if (activeId && future) {
      prueba = { auctionId, activeId, futureId: Number(future.itemId) };
      break;
    }
  }
  if (!prueba) throw new Error("No hay un lote futuro disponible para ejecutar la prueba");

  const attempt = await json(
    `${baseUrl}/api/auctions/${prueba.auctionId}/items/${prueba.futureId}/bids`,
    { method: "POST", headers, body: JSON.stringify({ clienteId: usuario.id, importe: 1 }) },
  );
  if (attempt.response.status !== 409 || Number(attempt.body.itemActivoId) !== prueba.activeId) {
    throw new Error(`Se esperaba HTTP 409 por lote inactivo y se recibió ${attempt.response.status}`);
  }
  console.log(JSON.stringify({
    ok: true,
    subastaId: prueba.auctionId,
    loteActivo: prueba.activeId,
    loteRechazado: prueba.futureId,
    status: attempt.response.status,
    mensaje: attempt.body.error,
  }));
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
