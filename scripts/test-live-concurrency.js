const baseUrl = process.env.API_BASE_URL || "https://auct-io-api.onrender.com";

async function login(documento) {
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ documento, clave: "1234" }),
  });
  if (!response.ok) throw new Error(`Login ${documento}: HTTP ${response.status}`);
  return response.json();
}

async function firstLiveEvent(token, auctionId) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(`${baseUrl}/api/auctions/${auctionId}/events`, {
      headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}` },
      signal: controller.signal,
    });
    if (!response.ok || !response.body) throw new Error(`SSE HTTP ${response.status}`);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) throw new Error("El stream finalizó sin enviar estado");
      buffer += decoder.decode(value, { stream: true });
      const end = buffer.indexOf("\n\n");
      if (end >= 0) {
        const line = buffer.slice(0, end).split("\n").find((x) => x.startsWith("data: "));
        if (line) return JSON.parse(line.slice(6));
        buffer = buffer.slice(end + 2);
      }
    }
  } finally {
    clearTimeout(timeout);
    controller.abort();
  }
}

async function main() {
  const [a, b] = await Promise.all([login("51000001"), login("51000002")]);
  const [stateA, stateB] = await Promise.all([
    firstLiveEvent(a.token, 7),
    firstLiveEvent(b.token, 7),
  ]);
  if (stateA.subastaId !== stateB.subastaId
      || stateA.itemActual?.itemId !== stateB.itemActual?.itemId
      || Number(stateA.mejorOferta) !== Number(stateB.mejorOferta)) {
    throw new Error("Los dos clientes recibieron estados diferentes");
  }
  console.log(JSON.stringify({
    ok: true,
    clientesSimultaneos: 2,
    subastaId: stateA.subastaId,
    itemId: stateA.itemActual?.itemId || null,
    mejorOferta: stateA.mejorOferta,
    fase: stateA.fase,
  }));
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
