const WebSocket = require("ws");
const baseUrl = process.env.API_BASE_URL || "https://auct-io-api.onrender.com";
const wsBase = baseUrl.replace(/^https:/, "wss:").replace(/^http:/, "ws:");
const auctionId = Number(process.env.TEST_AUCTION_ID || 12);

async function main() {
  const loginResponse = await fetch(`${baseUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ documento: "51000003", clave: "1234" }),
  });
  const session = await loginResponse.json();
  if (!loginResponse.ok) throw new Error(session.error || "No se pudo iniciar sesión");

  await new Promise((resolve, reject) => {
    const socket = new WebSocket(
      `${wsBase}/ws?auctionId=${auctionId}&clientId=${session.usuario.id}`,
      { headers: { Authorization: `Bearer ${session.token}` } },
    );
    const timeout = setTimeout(() => {
      socket.terminate();
      reject(new Error("No se recibió la actualización de la puja por WebSocket"));
    }, 20000);
    let ofertaEnviada = false;
    let importeEsperado = 0;
    let itemId = 0;

    socket.on("message", async (raw) => {
      const event = JSON.parse(String(raw));
      if (event.tipo !== "catalog-state") return;
      if (!ofertaEnviada) {
        const item = event.lotes.find((lote) => lote.vendido !== "si");
        if (!item) return reject(new Error("No hay lotes abiertos para la prueba"));
        ofertaEnviada = true;
        itemId = Number(item.itemId);
        importeEsperado = Number((Number(item.mejorOferta) + Number(item.precioBase) * 0.02).toFixed(2));
        const bidResponse = await fetch(`${baseUrl}/api/auctions/${auctionId}/items/${itemId}/bids`, {
          method: "POST",
          headers: { Authorization: `Bearer ${session.token}`, "Content-Type": "application/json" },
          body: JSON.stringify({ clienteId: session.usuario.id, importe: importeEsperado }),
        });
        if (!bidResponse.ok) {
          const error = await bidResponse.json();
          clearTimeout(timeout);
          socket.close();
          reject(new Error(error.error || `Puja HTTP ${bidResponse.status}`));
        }
        return;
      }

      if (event.motivo === "puja-confirmada") {
        const actualizado = event.lotes.find((lote) => Number(lote.itemId) === itemId);
        if (Number(actualizado?.mejorOferta) !== importeEsperado) {
          return reject(new Error("El WebSocket no reflejó el importe confirmado"));
        }
        const reinicioEsperado = Number(actualizado.duracionItemMinutos) * 60;
        if (Number(actualizado.segundosRestantes) < reinicioEsperado - 5) {
          return reject(new Error("La puja no reinició el contador independiente del lote"));
        }
        if (Number(actualizado.ultimasPujas?.[0]?.importe) !== importeEsperado) {
          return reject(new Error("La nueva puja no apareció en el mini historial"));
        }
        clearTimeout(timeout);
        socket.close();
        console.log(JSON.stringify({ ok: true, transporte: "WebSocket", itemId,
          mejorOferta: importeEsperado, relojReiniciadoSegundos: actualizado.segundosRestantes,
          historialActualizado: true, evento: event.motivo }));
        resolve();
      }
    });
    socket.on("error", reject);
  });
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
