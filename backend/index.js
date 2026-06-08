const express = require("express");
const cors = require("cors");
const { poolPromise, sql } = require("./db");

const app = express();

app.use(cors());
app.use(express.json({ limit: "25mb" }));
app.use((req, res, next) => {
  const override = req.header("X-HTTP-Method-Override");
  if (req.method === "POST" && override) {
    req.method = override.toUpperCase();
  }
  next();
});

const PORT = process.env.PORT || 3000;

/* ============================================================
   FUNCIONES AUXILIARES
   ============================================================ */

function categoriaValor(categoria) {
  const orden = {
    comun: 1,
    especial: 2,
    plata: 3,
    oro: 4,
    platino: 5,
  };

  return orden[categoria] || 0;
}

function crearTokenDemo(usuario) {
  const payload = {
    sub: usuario.id,
    documento: usuario.documento,
    categoria: usuario.categoria,
    rol: usuario.esAdmin ? "empleado" : "cliente",
    iat: new Date().toISOString(),
  };

  return Buffer.from(JSON.stringify(payload))
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function decodificarTokenDemo(token) {
  try {
    const base64 = String(token || "").replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(Buffer.from(padded, "base64").toString("utf8"));
  } catch (err) {
    return null;
  }
}

async function requireEmployee(req, res, next) {
  try {
    const authHeader = req.header("Authorization") || "";
    const [scheme, token] = authHeader.split(" ");

    if (scheme !== "Bearer" || !token) {
      return res.status(401).json({ error: "Debe enviar token Bearer" });
    }

    const payload = decodificarTokenDemo(token);
    if (!payload || !payload.sub) {
      return res.status(401).json({ error: "Token invalido" });
    }

    const pool = await poolPromise;
    const empleadoResult = await pool
      .request()
      .input("userId", sql.Int, payload.sub)
      .query(`
        SELECT e.identificador
        FROM Employees e
        INNER JOIN Users u
          ON e.identificador = u.identificador
        WHERE e.identificador = @userId
          AND u.estado = 'activo'
      `);

    if (empleadoResult.recordset.length === 0) {
      return res.status(403).json({
        error: "Solo personal interno puede realizar esta operacion",
      });
    }

    req.usuario = payload;
    next();
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
}

function calcularLimitesPuja(precioBase, mayorOferta, categoria) {
  const base = Number(precioBase);
  const mayor = Number(mayorOferta);
  const valorReferencia = mayor > 0 ? mayor : base;
  const categoriaPremium = categoria === "oro" || categoria === "platino";

  return {
    valorReferencia,
    pujaMinima: categoriaPremium ? valorReferencia + 0.01 : valorReferencia + base * 0.01,
    pujaMaxima: categoriaPremium ? null : valorReferencia + base * 0.2,
  };
}

async function obtenerEstadoVivo(pool, auctionId) {
  const result = await pool
    .request()
    .input("auctionId", sql.Int, auctionId)
    .query(`
      SELECT TOP 1
        a.identificador AS subastaId,
        a.estado,
        a.categoria,
        a.moneda,
        a.fecha        AS subastaFecha,
        a.hora         AS subastaHora,
        a.duracionItemMinutos,
        ci.identificador AS itemId,
        ci.precioBase,
        ci.comision,
        ci.subastado,
        ci.vendido,
        p.descripcionCatalogo,
        p.descripcionCompleta,
        ISNULL(MAX(b.importe), 0) AS mayorOferta,
        -- Base del timer: última puja si existe; si no, el horario programado de la subasta
        DATEDIFF(SECOND, GETDATE(), DATEADD(MINUTE, a.duracionItemMinutos,
          COALESCE(
            MAX(b.fechaHora),
            CAST(CONVERT(varchar(10), a.fecha, 23) + ' ' + CONVERT(varchar(8), a.hora, 108) AS DATETIME)
          )
        )) AS segundosRestantes
      FROM Auctions a
      INNER JOIN Catalogs c
        ON c.subasta = a.identificador
      INNER JOIN CatalogItems ci
        ON ci.catalogo = c.identificador
      INNER JOIN Products p
        ON p.identificador = ci.producto
      LEFT JOIN Bids b
        ON b.item = ci.identificador
      WHERE a.identificador = @auctionId
        AND ci.vendido = 'no'
      GROUP BY
        a.identificador,
        a.estado,
        a.categoria,
        a.moneda,
        a.fecha,
        a.hora,
        a.duracionItemMinutos,
        ci.identificador,
        ci.precioBase,
        ci.comision,
        ci.subastado,
        ci.vendido,
        p.descripcionCatalogo,
        p.descripcionCompleta
      ORDER BY ci.identificador
    `);

  if (result.recordset.length === 0) {
    return null;
  }

  const estado = result.recordset[0];
  const limites = calcularLimitesPuja(
    estado.precioBase,
    estado.mayorOferta,
    estado.categoria
  );

  return {
    subastaId: estado.subastaId,
    estado: estado.estado,
    categoria: estado.categoria,
    moneda: estado.moneda,
    itemActual: {
      itemId: estado.itemId,
      descripcionCatalogo: estado.descripcionCatalogo,
      descripcionCompleta: estado.descripcionCompleta,
      precioBase: estado.precioBase,
      comision: estado.comision,
      subastado: estado.subastado,
      vendido: estado.vendido,
    },
    mejorOferta: Number(estado.mayorOferta),
    pujaMinima: Number(limites.pujaMinima.toFixed(2)),
    pujaMaxima: limites.pujaMaxima === null ? null : Number(limites.pujaMaxima.toFixed(2)),
    segundosRestantes: Math.max(Number(estado.segundosRestantes || 0), 0),
    duracionItemMinutos: estado.duracionItemMinutos,
    mecanismoTiempoReal: "SSE",
    eventosUrl: `/api/auctions/${auctionId}/events`,
  };
}

// Auto-closes an item when the countdown reaches zero.
// Marks it sold and flags the winning bid (if any). Sale record creation is attempted
// but the item is always marked closed regardless of payment-method availability.
async function cerrarItemAutomatico(pool, itemId, auctionId) {
  // Guard: skip if already closed
  const check = await pool
    .request()
    .input("itemId", sql.Int, itemId)
    .query("SELECT vendido FROM CatalogItems WHERE identificador = @itemId");
  if (!check.recordset.length || check.recordset[0].vendido === "si") return;

  // Get top bid
  const winnerResult = await pool
    .request()
    .input("itemId", sql.Int, itemId)
    .query(`
      SELECT TOP 1
        b.identificador AS bidId,
        b.importe,
        at.cliente
      FROM Bids b
      INNER JOIN Attendees at ON b.asistente = at.identificador
      WHERE b.item = @itemId
      ORDER BY b.importe DESC, b.fechaHora ASC
    `);

  // Mark item as closed
  await pool
    .request()
    .input("itemId", sql.Int, itemId)
    .query("UPDATE CatalogItems SET subastado = 'si', vendido = 'si' WHERE identificador = @itemId");

  // Check if auction has any remaining unsold items — if not, close the auction
  const restantesResult = await pool
    .request()
    .input("auctionId", sql.Int, auctionId)
    .query(`
      SELECT COUNT(*) AS pendientes
      FROM CatalogItems ci
      INNER JOIN Catalogs c ON ci.catalogo = c.identificador
      WHERE c.subasta = @auctionId AND ci.vendido = 'no'
    `);
  if (restantesResult.recordset[0].pendientes === 0) {
    await pool
      .request()
      .input("auctionId", sql.Int, auctionId)
      .query("UPDATE Auctions SET estado = 'cerrada' WHERE identificador = @auctionId AND estado != 'cerrada'");
    console.log(`[AUTO-CLOSE] Subasta ${auctionId} marcada como CERRADA — sin items pendientes.`);
  }

  if (winnerResult.recordset.length === 0) {
    console.log(`[AUTO-CLOSE] Item ${itemId} cerrado sin pujas.`);
    return;
  }

  const ganador = winnerResult.recordset[0];

  // Mark winning bid
  await pool
    .request()
    .input("itemId", sql.Int, itemId)
    .query("UPDATE Bids SET ganador = 'no' WHERE item = @itemId");
  await pool
    .request()
    .input("bidId", sql.Int, ganador.bidId)
    .query("UPDATE Bids SET ganador = 'si' WHERE identificador = @bidId");

  console.log(`[AUTO-CLOSE] Item ${itemId} vendido. Ganador clienteId=${ganador.cliente} importe=${ganador.importe}`);

  // Try to create the AuctionRecord (best-effort; item already marked closed above)
  try {
    const itemData = await pool
      .request()
      .input("itemId", sql.Int, itemId)
      .query(`
        SELECT ci.precioBase, ci.comision, c.subasta AS subastaId, p.duenio, ci.producto AS productoId, a.moneda
        FROM CatalogItems ci
        INNER JOIN Catalogs c ON ci.catalogo = c.identificador
        INNER JOIN Auctions a ON c.subasta = a.identificador
        INNER JOIN Products p ON p.identificador = ci.producto
        WHERE ci.identificador = @itemId
      `);
    if (!itemData.recordset.length) return;
    const item = itemData.recordset[0];

    const medioResult = await pool
      .request()
      .input("cliente", sql.Int, ganador.cliente)
      .input("moneda", sql.VarChar, item.moneda)
      .query(`
        SELECT TOP 1 identificador FROM PaymentMethods
        WHERE cliente = @cliente AND verificado = 'si' AND moneda = @moneda
          AND (@moneda = 'pesos' OR tipo = 'cuenta_bancaria'
               OR (tipo = 'tarjeta_credito' AND esExtranjera = 'si')
               OR tipo = 'cheque_certificado')
        ORDER BY identificador
      `);
    const medioPagoId = medioResult.recordset.length > 0
      ? medioResult.recordset[0].identificador
      : null;

    // comision must be > 0.01 per DB constraint
    const comisionVal = Math.max(Number(item.comision) || 0, 0.02);

    await pool
      .request()
      .input("subasta", sql.Int, item.subastaId)
      .input("duenio", sql.Int, item.duenio)
      .input("producto", sql.Int, item.productoId)
      .input("cliente", sql.Int, ganador.cliente)
      .input("medioPago", sql.Int, medioPagoId)
      .input("importe", sql.Decimal(18, 2), Number(ganador.importe))
      .input("comision", sql.Decimal(18, 2), comisionVal)
      .query(`
        INSERT INTO AuctionRecords
          (subasta, duenio, producto, cliente, medioPago, importe, comision, costoEnvio, estadoPago, retiroPersonal)
        VALUES
          (@subasta, @duenio, @producto, @cliente, @medioPago, @importe, @comision, 0, 'pendiente', 'no')
      `);

    await pool
      .request()
      .input("producto", sql.Int, item.productoId)
      .input("nuevoDuenio", sql.Int, ganador.cliente)
      .query("UPDATE Products SET duenio = @nuevoDuenio, disponible = 'no' WHERE identificador = @producto");

    await pool
      .request()
      .input("cliente", sql.Int, ganador.cliente)
      .input("titulo", sql.VarChar, "Compra adjudicada")
      .input("mensaje", sql.VarChar, "Ganaste una subasta. Tenés una compra pendiente de pago. Revisá Compras y Pagos.")
      .query(`INSERT INTO Notifications (cliente, titulo, mensaje, leida) VALUES (@cliente, @titulo, @mensaje, 'no')`);

    console.log(`[AUTO-CLOSE] ✅ AuctionRecord creado — item=${itemId} cliente=${ganador.cliente} importe=${ganador.importe} medioPago=${medioPagoId ?? "null"}`);
  } catch (e) {
    console.error(`[AUTO-CLOSE] ❌ Error al crear AuctionRecord para item ${itemId}:`, e.message);
    console.error(`[AUTO-CLOSE]    detalle:`, e);
  }
}

function categoriaValida(categoria) {
  return ["comun", "especial", "plata", "oro", "platino"].includes(categoria);
}

function normalizarSiNo(valor, valorPorDefecto = "no") {
  if (valor === true || valor === "si") return "si";
  if (valor === false || valor === "no") return "no";
  return valorPorDefecto;
}

function base64ABuffer(valor) {
  if (!valor) return null;
  const limpio = String(valor).includes(",") ? String(valor).split(",").pop() : String(valor);
  return Buffer.from(limpio, "base64");
}

/* ============================================================
   TEST DE API
   ============================================================ */

app.get("/api/test", async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request().query("SELECT GETDATE() AS fechaServidor");

    res.status(200).json({
      ok: true,
      mensaje: "API conectada correctamente",
      fechaServidor: result.recordset[0].fechaServidor,
    });
  } catch (err) {
    res.status(500).json({
      ok: false,
      error: err.message,
    });
  }
});

/* ============================================================
   AUTH / LOGIN
   ============================================================ */

app.post("/api/auth/registro/paso1", async (req, res) => {
  try {
    const {
      documento,
      nombre,
      apellido,
      email,
      telefono,
      direccion,
      numeroPais,
      fotoDniFrente,
      fotoDniDorso,
    } = req.body;

    if (!documento || !nombre || !apellido || !direccion || !fotoDniFrente || !fotoDniDorso) {
      return res.status(400).json({
        error: "Debe enviar documento, nombre, apellido, domicilio legal y fotos de DNI frente/dorso",
      });
    }

    const pool = await poolPromise;

    const existente = await pool
      .request()
      .input("documento", sql.VarChar, documento)
      .query("SELECT identificador FROM Users WHERE documento = @documento");

    if (existente.recordset.length > 0) {
      return res.status(409).json({
        error: "Ya existe un usuario registrado con ese documento",
      });
    }

    const insertUser = await pool
      .request()
      .input("documento", sql.VarChar, documento)
      .input("nombre", sql.VarChar, nombre)
      .input("apellido", sql.VarChar, apellido)
      .input("email", sql.VarChar, email || null)
      .input("telefono", sql.VarChar, telefono || null)
      .input("direccion", sql.VarChar, direccion)
      .input("fotoDniFrente", sql.VarBinary(sql.MAX), base64ABuffer(fotoDniFrente))
      .input("fotoDniDorso", sql.VarBinary(sql.MAX), base64ABuffer(fotoDniDorso))
      .query(`
        INSERT INTO Users (
          documento,
          nombre,
          apellido,
          email,
          telefono,
          direccion,
          fotoDniFrente,
          fotoDniDorso,
          estado,
          clave
        )
        OUTPUT INSERTED.identificador
        VALUES (
          @documento,
          @nombre,
          @apellido,
          @email,
          @telefono,
          @direccion,
          @fotoDniFrente,
          @fotoDniDorso,
          'pendiente',
          NULL
        )
      `);

    const userId = insertUser.recordset[0].identificador;
    const paisNumerico = Number(numeroPais);
    let pais = Number.isInteger(paisNumerico) ? paisNumerico : null;

    if (pais !== null) {
      const paisResult = await pool
        .request()
        .input("numeroPais", sql.Int, pais)
        .query("SELECT numero FROM Countries WHERE numero = @numeroPais");

      if (paisResult.recordset.length === 0) {
        pais = null;
      }
    }

    await pool
      .request()
      .input("userId", sql.Int, userId)
      .input("numeroPais", sql.Int, pais)
      .query(`
        INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
        VALUES (@userId, @numeroPais, 'no', 'comun', NULL)
      `);

    await pool
      .request()
      .input("userId", sql.Int, userId)
      .input("numeroPais", sql.Int, pais)
      .query(`
        INSERT INTO Owners (
          identificador,
          numeroPais,
          verificacionFinanciera,
          verificacionJudicial,
          calificacionRiesgo,
          verificador
        )
        VALUES (@userId, @numeroPais, 'no', 'no', 6, NULL)
      `);

    res.status(201).json({
      mensaje: "Solicitud de registro recibida. La cuenta queda pendiente de verificacion.",
      usuarioId: userId,
      estadoRegistro: "pendiente_verificacion",
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/auth/registro/paso2", async (req, res) => {
  try {
    const { documento, clave } = req.body;

    if (!documento || !clave) {
      return res.status(400).json({
        error: "Debe enviar documento y clave",
      });
    }

    if (String(clave).length < 4) {
      return res.status(400).json({
        error: "La clave debe tener al menos 4 caracteres",
      });
    }

    const pool = await poolPromise;

    const usuarioResult = await pool
      .request()
      .input("documento", sql.VarChar, documento)
      .query(`
        SELECT
          u.identificador,
          u.estado,
          c.admitido
        FROM Users u
        INNER JOIN Clients c
          ON u.identificador = c.identificador
        WHERE u.documento = @documento
      `);

    if (usuarioResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Usuario no encontrado",
      });
    }

    const usuario = usuarioResult.recordset[0];

    if (usuario.estado !== "activo" || usuario.admitido !== "si") {
      return res.status(403).json({
        error: "La cuenta todavia esta pendiente de verificacion",
      });
    }

    await pool
      .request()
      .input("documento", sql.VarChar, documento)
      .input("clave", sql.VarChar, clave)
      .query(`
        UPDATE Users
        SET clave = @clave
        WHERE documento = @documento
      `);

    res.status(200).json({
      mensaje: "Clave generada correctamente. Ya puede iniciar sesion.",
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.patch("/api/admin/users/:userId/verification", requireEmployee, async (req, res) => {
  try {
    const { admitido, categoria, verificador } = req.body;
    const admitidoNormalizado = normalizarSiNo(admitido);
    const categoriaAsignada = categoriaValida(categoria) ? categoria : "comun";
    const estadoUsuario = admitidoNormalizado === "si" ? "activo" : "rechazado";

    const pool = await poolPromise;

    const usuarioResult = await pool
      .request()
      .input("userId", sql.Int, req.params.userId)
      .query("SELECT identificador FROM Users WHERE identificador = @userId");

    if (usuarioResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Usuario no encontrado",
      });
    }

    await pool
      .request()
      .input("userId", sql.Int, req.params.userId)
      .input("estado", sql.VarChar, estadoUsuario)
      .query(`
        UPDATE Users
        SET estado = @estado
        WHERE identificador = @userId
      `);

    await pool
      .request()
      .input("userId", sql.Int, req.params.userId)
      .input("admitido", sql.VarChar, admitidoNormalizado)
      .input("categoria", sql.VarChar, categoriaAsignada)
      .input("verificador", sql.Int, verificador || null)
      .query(`
        UPDATE Clients
        SET
          admitido = @admitido,
          categoria = @categoria,
          verificador = @verificador
        WHERE identificador = @userId
      `);

    res.status(200).json({
      mensaje: "Verificacion de usuario actualizada",
      usuarioId: Number(req.params.userId),
      estado: estadoUsuario,
      admitido: admitidoNormalizado,
      categoria: categoriaAsignada,
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/admin/users/pending", requireEmployee, async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request().query(`
      SELECT
        u.identificador AS id,
        u.documento,
        u.nombre,
        u.apellido,
        u.email,
        u.direccion,
        CAST('' AS XML).value('xs:base64Binary(sql:column("u.fotoDniFrente"))', 'VARCHAR(MAX)') AS fotoDniFrenteBase64,
        CAST('' AS XML).value('xs:base64Binary(sql:column("u.fotoDniDorso"))', 'VARCHAR(MAX)') AS fotoDniDorsoBase64,
        c.categoria,
        c.admitido
      FROM Users u
      INNER JOIN Clients c
        ON u.identificador = c.identificador
      WHERE u.estado = 'pendiente'
         OR c.admitido = 'no'
      ORDER BY u.fechaAlta DESC
    `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/auth/login", async (req, res) => {
  try {
    const { documento, clave } = req.body;

    if (!documento || !clave) {
      return res.status(400).json({
        error: "Debe ingresar documento y clave",
      });
    }

    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("documento", sql.VarChar, documento)
      .input("clave", sql.VarChar, clave)
      .query(`
        SELECT
          u.identificador AS id,
          u.documento,
          u.nombre,
          u.apellido,
          u.email,
          u.estado,
          ISNULL(c.admitido, 'no') AS admitido,
          ISNULL(c.categoria, 'interno') AS categoria,
          CASE WHEN e.identificador IS NULL THEN 0 ELSE 1 END AS esAdmin
        FROM Users u
        LEFT JOIN Clients c
          ON u.identificador = c.identificador
        LEFT JOIN Employees e
          ON u.identificador = e.identificador
        WHERE u.documento = @documento
          AND u.clave = @clave
      `);

    if (result.recordset.length === 0) {
      return res.status(401).json({
        error: "Documento o clave incorrecta",
      });
    }

    const usuario = result.recordset[0];

    if (usuario.estado !== "activo") {
      return res.status(403).json({
        error: "El usuario no se encuentra activo",
      });
    }

    if (!usuario.esAdmin && usuario.admitido !== "si") {
      return res.status(403).json({
        error: "El usuario todavia no fue admitido para participar",
      });
    }

    res.status(200).json({
      mensaje: "Login correcto",
      tokenType: "Bearer",
      token: crearTokenDemo(usuario),
      usuario,
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ============================================================
   USUARIOS / PERFIL
   ============================================================ */

app.get("/api/users/:userId", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("userId", sql.Int, req.params.userId)
      .query(`
        SELECT
          u.identificador AS id,
          u.documento,
          u.nombre,
          u.apellido,
          u.email,
          u.telefono,
          u.direccion,
          u.estado,
          ISNULL(c.admitido, 'no') AS admitido,
          ISNULL(c.categoria, 'interno') AS categoria,
          CASE WHEN e.identificador IS NULL THEN 0 ELSE 1 END AS esAdmin
        FROM Users u
        LEFT JOIN Clients c
          ON u.identificador = c.identificador
        LEFT JOIN Employees e
          ON u.identificador = e.identificador
        WHERE u.identificador = @userId
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Usuario no encontrado",
      });
    }

    res.status(200).json(result.recordset[0]);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.patch("/api/users/:userId", async (req, res) => {
  try {
    const pool = await poolPromise;
    const { telefono, direccion, email } = req.body;

    if (!telefono && !direccion && !email) {
      return res.status(400).json({ error: "No se proporcionaron campos a actualizar" });
    }

    let sets = [];
    const request = pool.request().input("userId", sql.Int, req.params.userId);

    if (telefono !== undefined) { sets.push("telefono = @telefono"); request.input("telefono", sql.NVarChar, telefono); }
    if (direccion !== undefined) { sets.push("direccion = @direccion"); request.input("direccion", sql.NVarChar, direccion); }
    if (email !== undefined)    { sets.push("email = @email");    request.input("email",    sql.NVarChar, email); }

    await request.query(`UPDATE Users SET ${sets.join(", ")} WHERE identificador = @userId`);

    res.status(200).json({ mensaje: "Perfil actualizado correctamente" });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/* ============================================================
   SUBASTAS
   ============================================================ */

app.get("/api/auctions", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool.request().query(`
      SELECT
        a.identificador AS id,
        a.fecha,
        a.hora,
        a.estado,
        a.ubicacion,
        a.capacidadAsistentes,
        a.tieneDeposito,
        a.seguridadPropia,
        a.categoria,
        a.moneda,
        u.nombre + ' ' + u.apellido AS subastador
      FROM Auctions a
      LEFT JOIN Auctioneers au
        ON a.subastador = au.identificador
      LEFT JOIN Users u
        ON au.identificador = u.identificador
      ORDER BY a.fecha, a.hora
    `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/clients/:clientId/auctions", async (req, res) => {
  try {
    const clientId = Number(req.params.clientId);
    const pool = await poolPromise;

    const clientResult = await pool
      .request()
      .input("clientId", sql.Int, clientId)
      .query(`
        SELECT
          u.estado,
          c.admitido,
          c.categoria,
          (
            SELECT COUNT(*)
            FROM PaymentMethods pm
            WHERE pm.cliente = c.identificador
              AND pm.verificado = 'si'
          ) AS mediosPagoVerificados,
          (
            SELECT COUNT(*)
            FROM Fines f
            WHERE f.cliente = c.identificador
              AND f.pagada = 'no'
          ) AS multasPendientes
        FROM Clients c
        INNER JOIN Users u
          ON c.identificador = u.identificador
        WHERE c.identificador = @clientId
      `);

    if (clientResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Cliente no encontrado",
      });
    }

    const cliente = clientResult.recordset[0];

    const auctionsResult = await pool.request().query(`
      SELECT
        a.identificador AS id,
        a.fecha,
        a.hora,
        a.estado,
        a.ubicacion,
        a.categoria,
        a.moneda
      FROM Auctions a
      WHERE a.estado IN ('abierta', 'en_curso', 'programada')
      ORDER BY a.fecha, a.hora
    `);

    const subastas = auctionsResult.recordset.map((subasta) => {
      const categoriaOk =
        categoriaValor(cliente.categoria) >= categoriaValor(subasta.categoria);

      const puedeVer =
        cliente.estado === "activo" && cliente.admitido === "si";

      const puedePujar =
        puedeVer &&
        categoriaOk &&
        cliente.mediosPagoVerificados > 0 &&
        cliente.multasPendientes <= 0;

      return {
        ...subasta,
        puedeVer,
        puedePujar,
        motivoBloqueo: puedePujar
          ? null
          : !puedeVer
          ? "Usuario no activo o no admitido"
          : cliente.multasPendientes > 0
          ? "Posee multas pendientes por impago"
          : !categoriaOk
          ? "Categoría insuficiente"
          : "No posee medio de pago verificado",
      };
    });

    res.status(200).json(subastas);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ── GET auction history for a client (subastas donde participó) ─────── */
app.get("/api/clients/:clientId/history", async (req, res) => {
  try {
    const clientId = Number(req.params.clientId);
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, clientId)
      .query(`
        SELECT DISTINCT
          a.identificador  AS id,
          a.fecha,
          a.hora,
          a.estado,
          a.ubicacion,
          a.categoria,
          a.moneda,
          (
            SELECT COUNT(*) FROM Bids b2
            INNER JOIN Attendees at2 ON b2.asistente = at2.identificador
            INNER JOIN CatalogItems ci2 ON b2.item = ci2.identificador
            INNER JOIN Catalogs c2 ON ci2.catalogo = c2.identificador
            WHERE c2.subasta = a.identificador AND at2.cliente = @clientId
          ) AS totalPujas,
          (
            SELECT COUNT(*) FROM AuctionRecords ar
            WHERE ar.subasta = a.identificador AND ar.cliente = @clientId
          ) AS itemsGanados
        FROM Bids b
        INNER JOIN Attendees at ON b.asistente = at.identificador
        INNER JOIN CatalogItems ci ON b.item = ci.identificador
        INNER JOIN Catalogs c ON ci.catalogo = c.identificador
        INNER JOIN Auctions a ON c.subasta = a.identificador
        WHERE at.cliente = @clientId
        ORDER BY a.fecha DESC, a.hora DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    console.error("[GET history]", err.message);
    res.status(500).json({ error: err.message });
  }
});

/* ── GET purchases for a client ────────────────────────────────────────── */
app.get("/api/clients/:clientId/purchases", async (req, res) => {
  try {
    const clientId = Number(req.params.clientId);
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, clientId)
      .query(`
        SELECT
          ar.identificador                          AS ventaId,
          ar.estadoPago,
          ar.importe,
          ar.comision,
          ISNULL(ar.costoEnvio, 0)                  AS costoEnvio,
          ar.fechaVenta,
          DATEADD(DAY, 7, ar.fechaVenta)            AS fechaLimitePago,
          ar.retiroPersonal,
          p.descripcionCatalogo,
          a.identificador                           AS subastaId,
          a.ubicacion,
          a.moneda
        FROM AuctionRecords ar
        INNER JOIN Products  p  ON ar.producto = p.identificador
        INNER JOIN Auctions  a  ON ar.subasta  = a.identificador
        WHERE ar.cliente = @clientId
        ORDER BY ar.fechaVenta DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    console.error("[GET purchases]", err.message);
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/auctions/:auctionId", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("auctionId", sql.Int, req.params.auctionId)
      .query(`
        SELECT
          a.identificador AS id,
          a.fecha,
          a.hora,
          a.estado,
          a.ubicacion,
          a.capacidadAsistentes,
          a.tieneDeposito,
          a.seguridadPropia,
          a.categoria,
          a.moneda,
          u.nombre + ' ' + u.apellido AS subastador
        FROM Auctions a
        LEFT JOIN Auctioneers au
          ON a.subastador = au.identificador
        LEFT JOIN Users u
          ON au.identificador = u.identificador
        WHERE a.identificador = @auctionId
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Subasta no encontrada",
      });
    }

    res.status(200).json(result.recordset[0]);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

// PATCH /api/admin/auctions/:auctionId/duracion  — change per-item timer duration
app.patch("/api/admin/auctions/:auctionId/duracion", requireEmployee, async (req, res) => {
  try {
    const { minutos } = req.body;
    const mins = parseInt(minutos, 10);
    if (!mins || mins < 1 || mins > 60) {
      return res.status(400).json({ error: "minutos debe ser un entero entre 1 y 60" });
    }
    const pool = await poolPromise;
    const result = await pool
      .request()
      .input("auctionId", sql.Int, req.params.auctionId)
      .input("minutos", sql.Int, mins)
      .query(`
        UPDATE Auctions
           SET duracionItemMinutos = @minutos
         WHERE identificador = @auctionId
      `);
    if (result.rowsAffected[0] === 0) {
      return res.status(404).json({ error: "Subasta no encontrada" });
    }
    res.json({ ok: true, duracionItemMinutos: mins });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/auctions/:auctionId/live-state", async (req, res) => {
  try {
    const pool = await poolPromise;
    const estado = await obtenerEstadoVivo(pool, req.params.auctionId);

    if (!estado) {
      return res.status(404).json({
        error: "No hay item activo para esta subasta",
      });
    }

    res.status(200).json(estado);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/auctions/:auctionId/events", async (req, res) => {
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.flushHeaders?.();

  let activo = true;

  const enviarEstado = async () => {
    if (!activo) return;

    try {
      const pool = await poolPromise;

      // Auto-start: if auction is 'programada' and its scheduled time has passed → en_curso
      await pool
        .request()
        .input("auctionId", sql.Int, req.params.auctionId)
        .query(`
          UPDATE Auctions
          SET estado = 'en_curso'
          WHERE identificador = @auctionId
            AND estado = 'programada'
            AND CAST(CONVERT(varchar(10), fecha, 23) + ' ' + CONVERT(varchar(8), hora, 108) AS DATETIME) <= GETDATE()
        `);

      let estado = await obtenerEstadoVivo(pool, req.params.auctionId);

      // Auto-close the item if its timer has expired
      if (estado && estado.segundosRestantes <= 0) {
        await cerrarItemAutomatico(pool, estado.itemActual.itemId, req.params.auctionId);
        // Re-fetch so the SSE sends the next unsold item (or null if auction is over)
        estado = await obtenerEstadoVivo(pool, req.params.auctionId);
      }

      if (!estado) {
        // No more items — check actual auction state
        const auctionCheck = await pool
          .request()
          .input("auctionId", sql.Int, req.params.auctionId)
          .query("SELECT estado FROM Auctions WHERE identificador = @auctionId");
        const estadoSubasta = auctionCheck.recordset[0]?.estado || "cerrada";
        res.write("event: live-state\n");
        res.write(`data: ${JSON.stringify({ finalizada: true, estado: estadoSubasta, subastaId: Number(req.params.auctionId) })}\n\n`);
      } else {
        res.write("event: live-state\n");
        res.write(`data: ${JSON.stringify(estado)}\n\n`);
      }
    } catch (err) {
      res.write("event: error\n");
      res.write(`data: ${JSON.stringify({ error: err.message })}\n\n`);
    }
  };

  await enviarEstado();
  const intervalo = setInterval(enviarEstado, 5000);

  req.on("close", () => {
    activo = false;
    clearInterval(intervalo);
    res.end();
  });
});

/* ============================================================
   CATÁLOGO
   ============================================================ */

app.get("/api/auctions/:auctionId/catalog", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("auctionId", sql.Int, req.params.auctionId)
      .query(`
        SELECT
          ci.identificador AS itemId,
          p.identificador AS productoId,
          p.descripcionCatalogo,
          p.descripcionCompleta,
          p.historia,
          p.artistaDiseniador,
          p.fechaObjeto,
          (
            SELECT TOP 1 ph.identificador
            FROM Photos ph
            WHERE ph.producto = p.identificador
            ORDER BY ph.orden, ph.identificador
          ) AS fotoPrincipalId,
          ci.precioBase,
          ci.comision,
          ci.subastado,
          ci.vendido,
          ISNULL(MAX(b.importe), ci.precioBase) AS mejorOferta
        FROM Catalogs c
        INNER JOIN CatalogItems ci
          ON c.identificador = ci.catalogo
        INNER JOIN Products p
          ON ci.producto = p.identificador
        LEFT JOIN Bids b
          ON ci.identificador = b.item
        WHERE c.subasta = @auctionId
        GROUP BY
          ci.identificador,
          p.identificador,
          p.descripcionCatalogo,
          p.descripcionCompleta,
          p.historia,
          p.artistaDiseniador,
          p.fechaObjeto,
          ci.precioBase,
          ci.comision,
          ci.subastado,
          ci.vendido
        ORDER BY ci.identificador
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/products/:productId/photos", async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool
      .request()
      .input("productId", sql.Int, req.params.productId)
      .query(`
        SELECT
          identificador AS id,
          orden,
          CAST('' AS XML).value('xs:base64Binary(sql:column("foto"))', 'VARCHAR(MAX)') AS fotoBase64
        FROM Photos
        WHERE producto = @productId
        ORDER BY orden, identificador
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/catalog-items/:itemId/best-bid", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("itemId", sql.Int, req.params.itemId)
      .query(`
        SELECT
          ci.identificador AS itemId,
          ci.precioBase,
          a.moneda,
          ISNULL(MAX(b.importe), ci.precioBase) AS mejorOferta
        FROM CatalogItems ci
        INNER JOIN Catalogs c
          ON ci.catalogo = c.identificador
        INNER JOIN Auctions a
          ON c.subasta = a.identificador
        LEFT JOIN Bids b
          ON ci.identificador = b.item
        WHERE ci.identificador = @itemId
        GROUP BY
          ci.identificador,
          ci.precioBase,
          a.moneda
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Ítem no encontrado",
      });
    }

    res.status(200).json(result.recordset[0]);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/catalog-items/:itemId/bids", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("itemId", sql.Int, req.params.itemId)
      .query(`
        SELECT
          b.identificador AS bidId,
          b.importe,
          b.fechaHora,
          b.ganador,
          u.nombre + ' ' + u.apellido AS postor
        FROM Bids b
        INNER JOIN Attendees a
          ON b.asistente = a.identificador
        INNER JOIN Users u
          ON a.cliente = u.identificador
        WHERE b.item = @itemId
        ORDER BY b.fechaHora DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ============================================================
   PUJAS
   ============================================================ */

async function crearPuja(req, res) {
  try {
    const { clienteId, subastaId, itemId, importe } = req.body;

    if (!clienteId || !subastaId || !itemId || !importe) {
      return res.status(400).json({
        error: "Debe enviar clienteId, subastaId, itemId e importe",
      });
    }

    const pool = await poolPromise;

    const clientResult = await pool
      .request()
      .input("clienteId", sql.Int, clienteId)
      .query(`
        SELECT
          u.estado,
          c.admitido,
          c.categoria,
          (
            SELECT COUNT(*)
            FROM PaymentMethods pm
            WHERE pm.cliente = c.identificador
              AND pm.verificado = 'si'
          ) AS mediosPagoVerificados,
          (
            SELECT COUNT(*)
            FROM Fines f
            WHERE f.cliente = c.identificador
              AND f.pagada = 'no'
          ) AS multasPendientes
        FROM Clients c
        INNER JOIN Users u
          ON c.identificador = u.identificador
        WHERE c.identificador = @clienteId
      `);

    if (clientResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Cliente no encontrado",
      });
    }

    const cliente = clientResult.recordset[0];

    if (cliente.estado !== "activo" || cliente.admitido !== "si") {
      return res.status(403).json({
        error: "Cliente no activo o no admitido",
      });
    }

    if (cliente.mediosPagoVerificados <= 0) {
      return res.status(403).json({
        error: "El cliente no posee medios de pago verificados",
      });
    }

    if (cliente.multasPendientes > 0) {
      return res.status(403).json({
        error: "El cliente posee multas pendientes por impago",
      });
    }

    const itemResult = await pool
      .request()
      .input("subastaId", sql.Int, subastaId)
      .input("itemId", sql.Int, itemId)
      .query(`
        SELECT
          ci.identificador AS itemId,
          ci.precioBase,
          ci.vendido,
          a.identificador AS subastaId,
          a.estado,
          a.categoria,
          a.moneda
        FROM CatalogItems ci
        INNER JOIN Catalogs c
          ON ci.catalogo = c.identificador
        INNER JOIN Auctions a
          ON c.subasta = a.identificador
        WHERE ci.identificador = @itemId
          AND a.identificador = @subastaId
      `);

    if (itemResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Ítem o subasta no encontrados",
      });
    }

    const item = itemResult.recordset[0];

    if (item.estado !== "abierta" && item.estado !== "en_curso") {
      return res.status(403).json({
        error: "La subasta no está abierta para recibir pujas",
      });
    }

    if (item.vendido === "si") {
      return res.status(409).json({
        error: "El ítem ya fue vendido",
      });
    }

    if (categoriaValor(cliente.categoria) < categoriaValor(item.categoria)) {
      return res.status(403).json({
        error: "La categoría del cliente no permite pujar en esta subasta",
      });
    }

    const medioCompatibleResult = await pool
      .request()
      .input("clienteId", sql.Int, clienteId)
      .input("moneda", sql.VarChar, item.moneda)
      .query(`
        SELECT
          COUNT(*) AS compatibles,
          SUM(CASE
            WHEN tipo = 'cheque_certificado'
              THEN ISNULL(montoDisponible, 0)
            ELSE 0
          END) AS garantiaDisponible
        FROM PaymentMethods
        WHERE cliente = @clienteId
          AND verificado = 'si'
          AND moneda = @moneda
          AND (
            @moneda = 'pesos'
            OR tipo = 'cuenta_bancaria'
            OR (tipo = 'tarjeta_credito' AND esExtranjera = 'si')
            OR tipo = 'cheque_certificado'
          )
      `);

    const mediosCompatibles = Number(medioCompatibleResult.recordset[0].compatibles || 0);
    const garantiaDisponible = Number(medioCompatibleResult.recordset[0].garantiaDisponible || 0);

    if (mediosCompatibles <= 0) {
      return res.status(403).json({
        error: "El cliente no posee un medio de pago verificado compatible con la moneda de la subasta",
      });
    }

    const maxBidResult = await pool
      .request()
      .input("itemId", sql.Int, itemId)
      .query(`
        SELECT ISNULL(MAX(importe), 0) AS mayorOferta
        FROM Bids
        WHERE item = @itemId
      `);

    const mayorOferta = Number(maxBidResult.recordset[0].mayorOferta);
    const precioBase = Number(item.precioBase);
    const importeNumerico = Number(importe);
    const valorReferencia = mayorOferta > 0 ? mayorOferta : precioBase;

    if (garantiaDisponible > 0 && importeNumerico > garantiaDisponible) {
      return res.status(403).json({
        error: `La puja supera la garantia disponible por cheque certificado (${garantiaDisponible.toFixed(2)})`,
      });
    }

    if (importeNumerico <= valorReferencia) {
      return res.status(400).json({
        error: `La puja debe ser mayor a ${valorReferencia.toFixed(2)}`,
      });
    }

    if (item.categoria !== "oro" && item.categoria !== "platino") {
      const minimoPermitido = valorReferencia + precioBase * 0.01;
      const maximoPermitido = valorReferencia + precioBase * 0.2;

      if (importeNumerico < minimoPermitido) {
        return res.status(400).json({
          error: `La puja mínima permitida es ${minimoPermitido.toFixed(2)}`,
        });
      }

      if (importeNumerico > maximoPermitido) {
        return res.status(400).json({
          error: `La puja máxima permitida es ${maximoPermitido.toFixed(2)}`,
        });
      }
    }

    let attendeeResult = await pool
      .request()
      .input("clienteId", sql.Int, clienteId)
      .input("subastaId", sql.Int, subastaId)
      .query(`
        SELECT identificador
        FROM Attendees
        WHERE cliente = @clienteId
          AND subasta = @subastaId
      `);

    let asistenteId;

    if (attendeeResult.recordset.length > 0) {
      asistenteId = attendeeResult.recordset[0].identificador;
    } else {
      const numeroPostor = Math.floor(Math.random() * 9000) + 1000;

      const insertAttendee = await pool
        .request()
        .input("numeroPostor", sql.Int, numeroPostor)
        .input("clienteId", sql.Int, clienteId)
        .input("subastaId", sql.Int, subastaId)
        .query(`
          INSERT INTO Attendees (numeroPostor, cliente, subasta)
          OUTPUT INSERTED.identificador
          VALUES (@numeroPostor, @clienteId, @subastaId)
        `);

      asistenteId = insertAttendee.recordset[0].identificador;
    }

    const insertBid = await pool
      .request()
      .input("asistenteId", sql.Int, asistenteId)
      .input("itemId", sql.Int, itemId)
      .input("importe", sql.Decimal(18, 2), importeNumerico)
      .query(`
        INSERT INTO Bids (asistente, item, importe, fechaHora, ganador)
        OUTPUT INSERTED.identificador, INSERTED.importe, INSERTED.fechaHora
        VALUES (@asistenteId, @itemId, @importe, GETDATE(), 'no')
      `);

    res.status(201).json({
      mensaje: "Puja registrada correctamente",
      puja: insertBid.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
}

app.post("/api/bids", crearPuja);

app.post("/api/auctions/:auctionId/items/:itemId/bids", (req, res) => {
  req.body = {
    ...req.body,
    subastaId: Number(req.params.auctionId),
    itemId: Number(req.params.itemId),
  };

  return crearPuja(req, res);
});

/* ============================================================
   MEDIOS DE PAGO
   ============================================================ */

app.get("/api/clients/:clientId/payment-methods", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          identificador AS id,
          tipo,
          entidad,
          numeroReferencia,
          esExtranjera,
          moneda,
          verificado,
          montoCheque,
          montoDisponible
        FROM PaymentMethods
        WHERE cliente = @clientId
        ORDER BY identificador DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/clients/:clientId/payment-methods", async (req, res) => {
  try {
    const {
      tipo,
      entidad,
      numeroReferencia,
      esExtranjera,
      moneda,
      montoCheque,
    } = req.body;

    const tiposValidos = ["cuenta_bancaria", "tarjeta_credito", "cheque_certificado"];
    const monedasValidas = ["pesos", "dolares"];
    const tipoNormalizado = String(tipo || "").trim();
    const monedaNormalizada = monedasValidas.includes(moneda) ? moneda : "pesos";
    const esExtranjeraNormalizado = normalizarSiNo(esExtranjera);
    const montoChequeNumerico =
      tipoNormalizado === "cheque_certificado" && montoCheque
        ? Number(montoCheque)
        : null;

    if (!tiposValidos.includes(tipoNormalizado)) {
      return res.status(400).json({
        error: "Tipo de medio de pago invalido",
      });
    }

    if (!entidad || !numeroReferencia) {
      return res.status(400).json({
        error: "Debe enviar entidad y referencia del medio de pago",
      });
    }

    if (
      tipoNormalizado === "cheque_certificado" &&
      (!montoChequeNumerico || montoChequeNumerico <= 0)
    ) {
      return res.status(400).json({
        error: "El cheque certificado debe informar un monto valido",
      });
    }

    const pool = await poolPromise;

    const clientResult = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query("SELECT identificador FROM Clients WHERE identificador = @clientId");

    if (clientResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Cliente no encontrado",
      });
    }

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .input("tipo", sql.VarChar, tipoNormalizado)
      .input("entidad", sql.VarChar, entidad)
      .input("numeroReferencia", sql.VarChar, numeroReferencia)
      .input("esExtranjera", sql.VarChar, esExtranjeraNormalizado)
      .input("moneda", sql.VarChar, monedaNormalizada)
      .input("montoCheque", sql.Decimal(18, 2), montoChequeNumerico)
      .input("montoDisponible", sql.Decimal(18, 2), montoChequeNumerico)
      .query(`
        INSERT INTO PaymentMethods (
          cliente,
          tipo,
          entidad,
          numeroReferencia,
          esExtranjera,
          moneda,
          verificado,
          montoCheque,
          montoDisponible
        )
        OUTPUT
          INSERTED.identificador AS id,
          INSERTED.tipo,
          INSERTED.entidad,
          INSERTED.numeroReferencia,
          INSERTED.esExtranjera,
          INSERTED.moneda,
          INSERTED.verificado,
          INSERTED.montoCheque,
          INSERTED.montoDisponible
        VALUES (
          @clientId,
          @tipo,
          @entidad,
          @numeroReferencia,
          @esExtranjera,
          @moneda,
          'no',
          @montoCheque,
          @montoDisponible
        )
      `);

    res.status(201).json({
      mensaje: "Medio de pago registrado. Queda pendiente de verificacion.",
      medioPago: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.patch("/api/clients/:clientId/payment-methods/:paymentMethodId", async (req, res) => {
  try {
    const { entidad, numeroReferencia, esExtranjera, moneda, montoCheque } = req.body;
    const monedasValidas = ["pesos", "dolares"];
    const monedaNormalizada = monedasValidas.includes(moneda) ? moneda : "pesos";
    const esExtranjeraNormalizado = normalizarSiNo(esExtranjera);

    if (!entidad || !numeroReferencia) {
      return res.status(400).json({ error: "Debe enviar entidad y referencia" });
    }

    const pool = await poolPromise;

    const check = await pool
      .request()
      .input("id", sql.Int, req.params.paymentMethodId)
      .input("clientId", sql.Int, req.params.clientId)
      .query("SELECT identificador, tipo FROM PaymentMethods WHERE identificador = @id AND cliente = @clientId");

    if (check.recordset.length === 0) {
      return res.status(404).json({ error: "Medio de pago no encontrado" });
    }

    const tipo = check.recordset[0].tipo;
    const montoChequeNumerico =
      tipo === "cheque_certificado" && montoCheque ? Number(montoCheque) : null;

    const result = await pool
      .request()
      .input("id", sql.Int, req.params.paymentMethodId)
      .input("entidad", sql.VarChar, entidad)
      .input("numeroReferencia", sql.VarChar, numeroReferencia)
      .input("esExtranjera", sql.VarChar, esExtranjeraNormalizado)
      .input("moneda", sql.VarChar, monedaNormalizada)
      .input("montoCheque", sql.Decimal(18, 2), montoChequeNumerico)
      .query(`
        UPDATE PaymentMethods
        SET
          entidad = @entidad,
          numeroReferencia = @numeroReferencia,
          esExtranjera = @esExtranjera,
          moneda = @moneda,
          verificado = 'no',
          montoCheque = ISNULL(@montoCheque, montoCheque),
          montoDisponible = ISNULL(@montoCheque, montoDisponible)
        OUTPUT
          INSERTED.identificador AS id,
          INSERTED.tipo,
          INSERTED.entidad,
          INSERTED.numeroReferencia,
          INSERTED.esExtranjera,
          INSERTED.moneda,
          INSERTED.verificado,
          INSERTED.montoCheque,
          INSERTED.montoDisponible
        WHERE identificador = @id
      `);

    res.status(200).json({
      mensaje: "Medio de pago actualizado. Queda pendiente de verificacion nuevamente.",
      medioPago: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.get("/api/admin/payment-methods/pending", requireEmployee, async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request().query(`
      SELECT
        pm.identificador AS id,
        pm.cliente,
        u.nombre + ' ' + u.apellido AS clienteNombre,
        pm.tipo,
        pm.entidad,
        pm.numeroReferencia,
        pm.esExtranjera,
        pm.moneda,
        pm.montoCheque,
        pm.montoDisponible,
        pm.verificado
      FROM PaymentMethods pm
      INNER JOIN Users u
        ON pm.cliente = u.identificador
      WHERE pm.verificado = 'no'
        AND ISNULL(pm.numeroReferencia, '') NOT LIKE 'RECHAZADO:%'
      ORDER BY pm.fechaAlta DESC
    `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.patch("/api/admin/payment-methods/:paymentMethodId/verification", requireEmployee, async (req, res) => {
  try {
    const { verificado, rechazado, motivoRechazo } = req.body;
    const verificadoNormalizado = normalizarSiNo(verificado);
    const estaRechazado = rechazado === true || rechazado === "si";
    const motivo = motivoRechazo || "Rechazado desde panel interno";
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("paymentMethodId", sql.Int, req.params.paymentMethodId)
      .input("verificado", sql.VarChar, verificadoNormalizado)
      .input("motivoRechazo", sql.VarChar, motivo)
      .input("estaRechazado", sql.Bit, estaRechazado ? 1 : 0)
      .query(`
        UPDATE PaymentMethods
        SET
          verificado = @verificado,
          numeroReferencia = CASE
            WHEN @estaRechazado = 1 AND ISNULL(numeroReferencia, '') NOT LIKE 'RECHAZADO:%'
              THEN LEFT('RECHAZADO: ' + @motivoRechazo + ' | ' + ISNULL(numeroReferencia, ''), 150)
            ELSE numeroReferencia
          END
        OUTPUT INSERTED.identificador AS id, INSERTED.verificado, INSERTED.numeroReferencia
        WHERE identificador = @paymentMethodId
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Medio de pago no encontrado",
      });
    }

    res.status(200).json({
      mensaje: estaRechazado
        ? "Medio de pago rechazado. Ya no figura como pendiente."
        : "Medio de pago verificado y habilitado para pujar.",
      medioPago: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ============================================================
   PRODUCTOS PARA CONSIGNACIÓN
   ============================================================ */

app.post("/api/products", async (req, res) => {
  try {
    const {
      duenio,
      descripcionCatalogo,
      descripcionCompleta,
      historia,
      artistaDiseniador,
      fechaObjeto,
      precioBaseSugerido,
      fotos,
      declaracionPropiedad,
      origenLicito,
      subastaId,        // opcional: subasta preferida elegida por el consignante
    } = req.body;

    if (!duenio || !descripcionCompleta || declaracionPropiedad !== "si") {
      return res.status(400).json({
        error: "Debe indicar dueño, descripción y declaración de propiedad",
      });
    }

    if (!Array.isArray(fotos) || fotos.filter(Boolean).length < 6) {
      return res.status(400).json({
        error: "Debe adjuntar o declarar al menos 6 fotos del articulo",
      });
    }

    const pool = await poolPromise;
    const historiaFinal = [
      historia || null,
      precioBaseSugerido
        ? `Precio base sugerido por el usuario: ${precioBaseSugerido}`
        : null,
    ]
      .filter(Boolean)
      .join("\n");

    const ownerResult = await pool
      .request()
      .input("duenio", sql.Int, duenio)
      .query("SELECT identificador FROM Owners WHERE identificador = @duenio");

    if (ownerResult.recordset.length === 0) {
      await pool
        .request()
        .input("duenio", sql.Int, duenio)
        .query(`
          INSERT INTO Owners (
            identificador,
            numeroPais,
            verificacionFinanciera,
            verificacionJudicial,
            calificacionRiesgo,
            verificador
          )
          VALUES (@duenio, NULL, 'no', 'no', 6, NULL)
        `);
    }

    const result = await pool
      .request()
      .input("duenio", sql.Int, duenio)
      .input("descripcionCatalogo", sql.VarChar, descripcionCatalogo || "")
      .input("descripcionCompleta", sql.VarChar, descripcionCompleta)
      .input("historia", sql.VarChar, historiaFinal || null)
      .input("artistaDiseniador", sql.VarChar, artistaDiseniador || null)
      .input("fechaObjeto", sql.Date, fechaObjeto || null)
      .input("declaracionPropiedad", sql.VarChar, declaracionPropiedad)
      .input("origenLicito", sql.VarChar, origenLicito || "si")
      .input("subastaPreferida", sql.Int, subastaId ? Number(subastaId) : null)
      .query(`
        INSERT INTO Products (
          fecha,
          disponible,
          descripcionCatalogo,
          descripcionCompleta,
          historia,
          artistaDiseniador,
          fechaObjeto,
          declaracionPropiedad,
          origenLicito,
          estadoAprobacion,
          duenio,
          subastaPreferida
        )
        OUTPUT INSERTED.identificador, INSERTED.estadoAprobacion, INSERTED.subastaPreferida
        VALUES (
          CAST(GETDATE() AS DATE),
          'no',
          @descripcionCatalogo,
          @descripcionCompleta,
          @historia,
          @artistaDiseniador,
          @fechaObjeto,
          @declaracionPropiedad,
          @origenLicito,
          'pendiente',
          @duenio,
          @subastaPreferida
        )
      `);

    const productoId = result.recordset[0].identificador;
    const fotosValidas = fotos.filter(Boolean).slice(0, 12);

    for (let i = 0; i < fotosValidas.length; i += 1) {
      await pool
        .request()
        .input("productoId", sql.Int, productoId)
        .input("foto", sql.VarBinary(sql.MAX), base64ABuffer(fotosValidas[i]))
        .input("orden", sql.Int, i + 1)
        .query(`
          INSERT INTO Photos (producto, foto, orden)
          VALUES (@productoId, @foto, @orden)
        `);
    }

    res.status(202).json({
      mensaje: "Articulo enviado para revision con fotos minimas recibidas",
      producto: {
        ...result.recordset[0],
        fotosRecibidas: fotosValidas.length,
        precioBaseSugerido: precioBaseSugerido || null,
      },
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/clients/:clientId/products", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          p.identificador AS id,
          p.descripcionCatalogo,
          p.descripcionCompleta,
          p.estadoAprobacion,
          p.motivoRechazo,
          p.ubicacionDeposito,
          p.seguro,
          p.fechaAlta,
          COUNT(ph.identificador) AS fotos
        FROM Products p
        LEFT JOIN Photos ph
          ON ph.producto = p.identificador
        WHERE p.duenio = @clientId
        GROUP BY
          p.identificador,
          p.descripcionCatalogo,
          p.descripcionCompleta,
          p.estadoAprobacion,
          p.motivoRechazo,
          p.ubicacionDeposito,
          p.seguro,
          p.fechaAlta
        ORDER BY p.fechaAlta DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/admin/products/pending", requireEmployee, async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request().query(`
      SELECT
        p.identificador AS id,
        p.descripcionCatalogo,
        p.descripcionCompleta,
        p.estadoAprobacion,
        p.duenio,
        u.nombre + ' ' + u.apellido AS duenioNombre,
        p.fechaAlta,
        p.subastaPreferida,
        a.ubicacion    AS subastaUbicacion,
        a.fecha        AS subastaFecha,
        a.hora         AS subastaHora,
        a.estado       AS subastaEstado,
        COUNT(ph.identificador) AS fotos
      FROM Products p
      INNER JOIN Users u
        ON p.duenio = u.identificador
      LEFT JOIN Photos ph
        ON ph.producto = p.identificador
      LEFT JOIN Auctions a
        ON a.identificador = p.subastaPreferida
      WHERE p.estadoAprobacion = 'pendiente'
      GROUP BY
        p.identificador,
        p.descripcionCatalogo,
        p.descripcionCompleta,
        p.estadoAprobacion,
        p.duenio,
        u.nombre,
        u.apellido,
        p.fechaAlta,
        p.subastaPreferida,
        a.ubicacion,
        a.fecha,
        a.hora,
        a.estado
      ORDER BY p.fechaAlta DESC
    `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.patch("/api/admin/products/:productId/review", requireEmployee, async (req, res) => {
  try {
    const {
      estadoAprobacion,
      motivoRechazo,
      ubicacionDeposito,
      seguro,
      revisor,
    } = req.body;

    if (!["aceptado", "rechazado"].includes(estadoAprobacion)) {
      return res.status(400).json({
        error: "Debe indicar estadoAprobacion aceptado o rechazado",
      });
    }

    if (estadoAprobacion === "rechazado" && !motivoRechazo) {
      return res.status(400).json({
        error: "Debe indicar motivo de rechazo",
      });
    }

    const pool = await poolPromise;

    const productoResult = await pool
      .request()
      .input("productId", sql.Int, req.params.productId)
      .query("SELECT identificador FROM Products WHERE identificador = @productId");

    if (productoResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Producto no encontrado",
      });
    }

    await pool
      .request()
      .input("productId", sql.Int, req.params.productId)
      .input("estadoAprobacion", sql.VarChar, estadoAprobacion)
      .input("motivoRechazo", sql.VarChar, motivoRechazo || null)
      .input("ubicacionDeposito", sql.VarChar, ubicacionDeposito || null)
      .input("seguro", sql.VarChar, seguro || null)
      .input("revisor", sql.Int, revisor || null)
      .input("disponible", sql.VarChar, estadoAprobacion === "aceptado" ? "si" : "no")
      .query(`
        UPDATE Products
        SET
          estadoAprobacion = @estadoAprobacion,
          motivoRechazo = @motivoRechazo,
          ubicacionDeposito = @ubicacionDeposito,
          seguro = @seguro,
          revisor = @revisor,
          disponible = @disponible
        WHERE identificador = @productId
      `);

    res.status(200).json({
      mensaje: "Revision de consignacion actualizada",
      productoId: Number(req.params.productId),
      estadoAprobacion,
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/admin/auctions/:auctionId/items", requireEmployee, async (req, res) => {
  try {
    const { productId, precioBase, comision, responsable } = req.body;
    const precioBaseNumerico = Number(precioBase);
    const comisionNumerica = Number(comision);

    if (!productId || !precioBaseNumerico || !comisionNumerica) {
      return res.status(400).json({
        error: "Debe enviar productId, precioBase y comision",
      });
    }

    const pool = await poolPromise;

    const productResult = await pool
      .request()
      .input("productId", sql.Int, productId)
      .query(`
        SELECT identificador
        FROM Products
        WHERE identificador = @productId
          AND estadoAprobacion = 'aceptado'
      `);

    if (productResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Producto aceptado no encontrado",
      });
    }

    let catalogResult = await pool
      .request()
      .input("auctionId", sql.Int, req.params.auctionId)
      .query(`
        SELECT TOP 1 identificador
        FROM Catalogs
        WHERE subasta = @auctionId
        ORDER BY identificador
      `);

    let catalogoId;

    if (catalogResult.recordset.length === 0) {
      let responsableCatalogo = responsable || null;

      if (!responsableCatalogo) {
        const responsableResult = await pool
          .request()
          .query("SELECT TOP 1 identificador FROM Employees ORDER BY identificador");

        if (responsableResult.recordset.length === 0) {
          return res.status(400).json({
            error: "Debe existir un empleado responsable para crear el catalogo",
          });
        }

        responsableCatalogo = responsableResult.recordset[0].identificador;
      }

      catalogResult = await pool
        .request()
        .input("auctionId", sql.Int, req.params.auctionId)
        .input("responsable", sql.Int, responsableCatalogo)
        .query(`
          INSERT INTO Catalogs (descripcion, subasta, responsable)
          OUTPUT INSERTED.identificador
          VALUES ('Catalogo generado desde admin', @auctionId, @responsable)
        `);
    }

    catalogoId = catalogResult.recordset[0].identificador;

    const insertResult = await pool
      .request()
      .input("catalogoId", sql.Int, catalogoId)
      .input("productId", sql.Int, productId)
      .input("precioBase", sql.Decimal(18, 2), precioBaseNumerico)
      .input("comision", sql.Decimal(18, 2), comisionNumerica)
      .query(`
        INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
        OUTPUT INSERTED.identificador AS itemId
        VALUES (@catalogoId, @productId, @precioBase, @comision, 'no', 'no')
      `);

    await pool
      .request()
      .input("productId", sql.Int, productId)
      .query("UPDATE Products SET disponible = 'si' WHERE identificador = @productId");

    res.status(201).json({
      mensaje: "Producto asignado a la subasta",
      item: insertResult.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/admin/auctions/:auctionId/items/:itemId/close", requireEmployee, async (req, res) => {
  try {
    const { medioPagoId, costoEnvio, retiroPersonal } = req.body;
    const pool = await poolPromise;

    const itemResult = await pool
      .request()
      .input("auctionId", sql.Int, req.params.auctionId)
      .input("itemId", sql.Int, req.params.itemId)
      .query(`
        SELECT
          ci.identificador AS itemId,
          ci.precioBase,
          ci.comision,
          ci.vendido,
          a.moneda,
          p.identificador AS productoId,
          p.duenio
        FROM CatalogItems ci
        INNER JOIN Catalogs c
          ON ci.catalogo = c.identificador
        INNER JOIN Auctions a
          ON c.subasta = a.identificador
        INNER JOIN Products p
          ON ci.producto = p.identificador
        WHERE ci.identificador = @itemId
          AND c.subasta = @auctionId
      `);

    if (itemResult.recordset.length === 0) {
      return res.status(404).json({
        error: "Item o subasta no encontrados",
      });
    }

    const item = itemResult.recordset[0];

    if (item.vendido === "si") {
      return res.status(409).json({
        error: "El item ya fue cerrado",
      });
    }

    const winnerResult = await pool
      .request()
      .input("itemId", sql.Int, req.params.itemId)
      .query(`
        SELECT TOP 1
          b.identificador AS bidId,
          b.importe,
          at.cliente
        FROM Bids b
        INNER JOIN Attendees at
          ON b.asistente = at.identificador
        WHERE b.item = @itemId
        ORDER BY b.importe DESC, b.fechaHora ASC
      `);

    if (winnerResult.recordset.length === 0) {
      await pool
        .request()
        .input("itemId", sql.Int, req.params.itemId)
        .query(`
          UPDATE CatalogItems
          SET subastado = 'si', vendido = 'si'
          WHERE identificador = @itemId
        `);

      return res.status(200).json({
        mensaje: "Item cerrado sin pujas. La empresa compra por el valor base.",
        itemId: Number(req.params.itemId),
        importe: Number(item.precioBase),
        comprador: "empresa",
      });
    }

    const ganador = winnerResult.recordset[0];
    let medioPagoFinal = medioPagoId || null;

    if (!medioPagoFinal) {
      const medioResult = await pool
        .request()
        .input("cliente", sql.Int, ganador.cliente)
        .input("moneda", sql.VarChar, item.moneda)
        .query(`
          SELECT TOP 1 identificador
          FROM PaymentMethods
          WHERE cliente = @cliente
            AND verificado = 'si'
            AND moneda = @moneda
            AND (
              @moneda = 'pesos'
              OR tipo = 'cuenta_bancaria'
              OR (tipo = 'tarjeta_credito' AND esExtranjera = 'si')
              OR tipo = 'cheque_certificado'
            )
          ORDER BY identificador
        `);

      if (medioResult.recordset.length > 0) {
        medioPagoFinal = medioResult.recordset[0].identificador;
      }
    }

    if (!medioPagoFinal) {
      return res.status(400).json({
        error: "El ganador no posee un medio de pago verificado compatible para generar la venta",
      });
    }

    await pool
      .request()
      .input("itemId", sql.Int, req.params.itemId)
      .query("UPDATE Bids SET ganador = 'no' WHERE item = @itemId");

    await pool
      .request()
      .input("bidId", sql.Int, ganador.bidId)
      .query("UPDATE Bids SET ganador = 'si' WHERE identificador = @bidId");

    const ventaResult = await pool
      .request()
      .input("auctionId", sql.Int, req.params.auctionId)
      .input("duenio", sql.Int, item.duenio)
      .input("producto", sql.Int, item.productoId)
      .input("cliente", sql.Int, ganador.cliente)
      .input("medioPago", sql.Int, medioPagoFinal)
      .input("importe", sql.Decimal(18, 2), Number(ganador.importe))
      .input("comision", sql.Decimal(18, 2), Number(item.comision))
      .input("costoEnvio", sql.Decimal(18, 2), costoEnvio ? Number(costoEnvio) : 0)
      .input("retiroPersonal", sql.VarChar, normalizarSiNo(retiroPersonal))
      .query(`
        INSERT INTO AuctionRecords (
          subasta,
          duenio,
          producto,
          cliente,
          medioPago,
          importe,
          comision,
          costoEnvio,
          estadoPago,
          retiroPersonal
        )
        OUTPUT INSERTED.identificador AS ventaId, INSERTED.estadoPago
        VALUES (
          @auctionId,
          @duenio,
          @producto,
          @cliente,
          @medioPago,
          @importe,
          @comision,
          @costoEnvio,
          'pendiente',
          @retiroPersonal
        )
      `);

    await pool
      .request()
      .input("itemId", sql.Int, req.params.itemId)
      .query(`
        UPDATE CatalogItems
        SET subastado = 'si', vendido = 'si'
        WHERE identificador = @itemId
      `);

    await pool
      .request()
      .input("producto", sql.Int, item.productoId)
      .input("nuevoDuenio", sql.Int, ganador.cliente)
      .query(`
        UPDATE Products
        SET duenio = @nuevoDuenio,
            disponible = 'no'
        WHERE identificador = @producto
      `);

    await pool
      .request()
      .input("medioPago", sql.Int, medioPagoFinal)
      .input("importe", sql.Decimal(18, 2), Number(ganador.importe))
      .query(`
        UPDATE PaymentMethods
        SET montoDisponible = CASE
          WHEN tipo = 'cheque_certificado' AND montoDisponible IS NOT NULL
            THEN montoDisponible - @importe
          ELSE montoDisponible
        END
        WHERE identificador = @medioPago
      `);

    await pool
      .request()
      .input("cliente", sql.Int, ganador.cliente)
      .input("titulo", sql.VarChar, "Compra adjudicada")
      .input("mensaje", sql.VarChar, "Ganaste una subasta. Tenes una compra pendiente de pago con importe, comision y envio.")
      .query(`
        INSERT INTO Notifications (cliente, titulo, mensaje, leida)
        VALUES (@cliente, @titulo, @mensaje, 'no')
      `);

    res.status(201).json({
      mensaje: "Item cerrado y venta generada",
      venta: ventaResult.recordset[0],
      ganador: {
        clienteId: ganador.cliente,
        importe: Number(ganador.importe),
      },
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ============================================================
   COMPRAS Y PAGOS
   ============================================================ */

app.get("/api/clients/:clientId/purchases", async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          ar.identificador AS ventaId,
          ar.importe,
          ar.comision,
          ISNULL(ar.costoEnvio, 0) AS costoEnvio,
          ar.estadoPago,
          ar.fechaVenta,
          DATEADD(DAY, 7, ar.fechaVenta) AS fechaLimitePago,
          ISNULL(ar.retiroPersonal, 'no') AS retiroPersonal,
          p.descripcionCatalogo,
          a.identificador AS subastaId
        FROM AuctionRecords ar
        INNER JOIN Products p ON ar.producto = p.identificador
        INNER JOIN Auctions a ON ar.subasta = a.identificador
        WHERE ar.cliente = @clientId
        ORDER BY ar.fechaVenta DESC
      `);
    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post("/api/purchases/:ventaId/pay", async (req, res) => {
  try {
    const pool = await poolPromise;
    const check = await pool
      .request()
      .input("ventaId", sql.Int, req.params.ventaId)
      .query("SELECT identificador, estadoPago FROM AuctionRecords WHERE identificador = @ventaId");
    if (!check.recordset.length)
      return res.status(404).json({ error: "Compra no encontrada" });
    if (check.recordset[0].estadoPago === "pagado")
      return res.status(409).json({ error: "La compra ya fue pagada" });

    await pool
      .request()
      .input("ventaId", sql.Int, req.params.ventaId)
      .query("UPDATE AuctionRecords SET estadoPago = 'pagado' WHERE identificador = @ventaId");

    res.status(200).json({ mensaje: "Pago registrado correctamente" });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/* ============================================================
   HISTORIAL / MÉTRICAS
   ============================================================ */

app.get("/api/clients/:clientId/history", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          a.identificador AS subastaId,
          a.fecha,
          a.hora,
          a.moneda,
          p.descripcionCatalogo,
          MAX(b.importe) AS mejorOfertaPropia,
          MAX(CASE WHEN b.ganador = 'si' THEN 1 ELSE 0 END) AS gano
        FROM Bids b
        INNER JOIN Attendees at
          ON b.asistente = at.identificador
        INNER JOIN CatalogItems ci
          ON b.item = ci.identificador
        INNER JOIN Products p
          ON ci.producto = p.identificador
        INNER JOIN Catalogs c
          ON ci.catalogo = c.identificador
        INNER JOIN Auctions a
          ON c.subasta = a.identificador
        WHERE at.cliente = @clientId
        GROUP BY
          a.identificador,
          a.fecha,
          a.hora,
          a.moneda,
          p.descripcionCatalogo
        ORDER BY a.fecha DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/clients/:clientId/purchases", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          ar.identificador AS ventaId,
          ar.subasta AS subastaId,
          p.descripcionCatalogo,
          ar.importe,
          ar.comision,
          ar.costoEnvio,
          ar.estadoPago,
          ar.retiroPersonal,
          ar.fechaVenta
        FROM AuctionRecords ar
        INNER JOIN Products p
          ON ar.producto = p.identificador
        WHERE ar.cliente = @clientId
        ORDER BY ar.fechaVenta DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/purchases/:purchaseId/pay", async (req, res) => {
  try {
    const { medioPagoId, retiroPersonal } = req.body;
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("purchaseId", sql.Int, req.params.purchaseId)
      .input("medioPagoId", sql.Int, medioPagoId || null)
      .input("retiroPersonal", sql.VarChar, retiroPersonal ? normalizarSiNo(retiroPersonal) : null)
      .query(`
        UPDATE AuctionRecords
        SET
          estadoPago = 'pagado',
          medioPago = ISNULL(@medioPagoId, medioPago),
          retiroPersonal = ISNULL(@retiroPersonal, retiroPersonal)
        OUTPUT
          INSERTED.identificador AS ventaId,
          INSERTED.estadoPago,
          INSERTED.medioPago,
          INSERTED.retiroPersonal
        WHERE identificador = @purchaseId
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Compra no encontrada",
      });
    }

    res.status(200).json({
      mensaje: "Pago registrado correctamente",
      compra: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.get("/api/clients/:clientId/fines", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          f.identificador AS id,
          f.subasta AS subastaId,
          f.monto,
          f.pagada,
          f.fechaGeneracion,
          a.fecha,
          a.hora,
          a.moneda
        FROM Fines f
        INNER JOIN Auctions a
          ON f.subasta = a.identificador
        WHERE f.cliente = @clientId
        ORDER BY f.fechaGeneracion DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.patch("/api/fines/:fineId/pay", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("fineId", sql.Int, req.params.fineId)
      .query(`
        UPDATE Fines
        SET pagada = 'si'
        OUTPUT INSERTED.identificador AS id, INSERTED.pagada
        WHERE identificador = @fineId
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Multa no encontrada",
      });
    }

    res.status(200).json({
      mensaje: "Multa marcada como pagada",
      multa: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/fines/:fineId/pay", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("fineId", sql.Int, req.params.fineId)
      .query(`
        UPDATE Fines
        SET pagada = 'si'
        OUTPUT INSERTED.identificador AS id, INSERTED.pagada
        WHERE identificador = @fineId
      `);

    if (result.recordset.length === 0) {
      return res.status(404).json({
        error: "Multa no encontrada",
      });
    }

    res.status(200).json({
      mensaje: "Multa marcada como pagada",
      multa: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

app.post("/api/admin/fines", requireEmployee, async (req, res) => {
  try {
    const { clienteId, subastaId, monto } = req.body;
    const montoNumerico = Number(monto);

    if (!clienteId || !subastaId || !montoNumerico || montoNumerico <= 0) {
      return res.status(400).json({
        error: "Debe enviar clienteId, subastaId y monto valido",
      });
    }

    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clienteId", sql.Int, clienteId)
      .input("subastaId", sql.Int, subastaId)
      .input("monto", sql.Decimal(18, 2), montoNumerico)
      .query(`
        INSERT INTO Fines (cliente, subasta, monto, pagada)
        OUTPUT INSERTED.identificador AS id, INSERTED.monto, INSERTED.pagada
        VALUES (@clienteId, @subastaId, @monto, 'no')
      `);

    res.status(201).json({
      mensaje: "Multa registrada por impago",
      multa: result.recordset[0],
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ============================================================
   NOTIFICACIONES
   ============================================================ */

app.get("/api/clients/:clientId/notifications", async (req, res) => {
  try {
    const pool = await poolPromise;

    const result = await pool
      .request()
      .input("clientId", sql.Int, req.params.clientId)
      .query(`
        SELECT
          identificador AS id,
          titulo,
          mensaje,
          fechaHora,
          leida
        FROM Notifications
        WHERE cliente = @clientId
        ORDER BY fechaHora DESC
      `);

    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({
      error: err.message,
    });
  }
});

/* ============================================================
   RUTAS ANTIGUAS COMPATIBLES
   ============================================================ */

app.get("/subastas", async (req, res) => {
  try {
    const pool = await poolPromise;
    const result = await pool.request().query("SELECT * FROM Auctions");
    res.status(200).json(result.recordset);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/* ============================================================
   INICIO SERVIDOR
   ============================================================ */

app.listen(PORT, () => {
  console.log(`API Subastas corriendo en http://localhost:${PORT}`);
});
