const WebSocket = require("ws");
const baseUrl = process.env.API_BASE_URL || "https://auct-io-api.onrender.com";
const wsBase = baseUrl.replace(/^https:/, "wss:").replace(/^http:/, "ws:");
const auctionId = Number(process.env.TEST_AUCTION_ID || 12);

async function login(documento) {
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ documento, clave: "1234" }),
  });
  if (!response.ok) throw new Error(`Login ${documento}: HTTP ${response.status}`);
  return response.json();
}

function firstCatalogState(session, auctionId) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(
      `${wsBase}/ws?auctionId=${auctionId}&clientId=${session.usuario.id}`,
      { headers: { Authorization: `Bearer ${session.token}` } },
    );
    const timeout = setTimeout(() => {
      socket.terminate();
      reject(new Error("El WebSocket no entregó el catálogo dentro del plazo"));
    }, 15000);
    socket.on("message", (raw) => {
      const event = JSON.parse(String(raw));
      if (event.tipo === "catalog-state") {
        clearTimeout(timeout);
        socket.close();
        resolve(event);
      }
    });
    socket.on("error", (error) => {
      clearTimeout(timeout);
      reject(error);
    });
  });
}

async function main() {
  const [a, b] = await Promise.all([login("51000001"), login("51000002")]);
  const [stateA, stateB] = await Promise.all([
    firstCatalogState(a, auctionId),
    firstCatalogState(b, auctionId),
  ]);
  const snapshotA = stateA.lotes.map((item) => `${item.itemId}:${item.mejorOferta}`).join("|");
  const snapshotB = stateB.lotes.map((item) => `${item.itemId}:${item.mejorOferta}`).join("|");
  if (stateA.subastaId !== stateB.subastaId || snapshotA !== snapshotB) {
    throw new Error("Los dos clientes recibieron catálogos diferentes");
  }
  if (!stateA.lotes.every((item) => Number.isFinite(item.segundosRestantes))) {
    throw new Error("Hay lotes sin contador independiente");
  }
  if (!stateA.lotes.every((item) => Array.isArray(item.ultimasPujas))) {
    throw new Error("Hay lotes sin historial resumido de pujas");
  }
  console.log(JSON.stringify({
    ok: true,
    transporte: "WebSocket",
    clientesSimultaneos: 2,
    subastaId: stateA.subastaId,
    lotesSincronizados: stateA.lotes.length,
    lotesAbiertos: stateA.lotes.filter((item) => item.vendido !== "si").length,
    contadoresIndependientes: stateA.lotes.length,
    historialesIncluidos: stateA.lotes.filter((item) => item.ultimasPujas.length > 0).length,
  }));
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
