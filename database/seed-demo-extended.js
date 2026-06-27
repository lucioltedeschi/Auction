const fs = require("fs");
const path = require("path");
const sql = require("mssql");

require("dotenv").config({ path: path.join(__dirname, "..", ".env"), quiet: true });

const DEMO_PREFIX = "DEMO XL - ";
const assetsDir = path.join(__dirname, "demo-assets");
const assetNames = [
  "arte-showroom.jpg",
  "joyas-showroom.jpg",
  "diseno-showroom.jpg",
  "coleccion-showroom.jpg",
  "vehiculos-showroom.jpg",
  "lifestyle-showroom.jpg",
];

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Falta ${name} en .env`);
  return value;
}

function daysFromToday(days) {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() + days);
  return date;
}

async function seed() {
  const images = assetNames.map((name) => ({ name, bytes: fs.readFileSync(path.join(assetsDir, name)) }));
  const pool = await new sql.ConnectionPool({
    user: required("DB_USER"),
    password: required("DB_PASSWORD"),
    server: required("DB_SERVER"),
    database: process.env.DB_NAME || "auction",
    port: Number(process.env.DB_PORT || 1433),
    options: { encrypt: true, trustServerCertificate: false },
    connectionTimeout: 30000,
    requestTimeout: 180000,
  }).connect();

  const transaction = new sql.Transaction(pool);
  await transaction.begin();

  async function query(statement, inputs = {}) {
    const request = new sql.Request(transaction);
    for (const [name, spec] of Object.entries(inputs)) {
      if (spec && spec.type) request.input(name, spec.type, spec.value);
      else request.input(name, spec);
    }
    return request.query(statement);
  }

  async function scalar(statement, inputs = {}, field = "id") {
    const result = await query(statement, inputs);
    return result.recordset[0]?.[field];
  }

  try {
    const adminId = await scalar("SELECT identificador AS id FROM Users WHERE documento = '20000111'");
    const auctioneerId = await scalar("SELECT identificador AS id FROM Users WHERE documento = '20000222'");
    if (!adminId || !auctioneerId) throw new Error("Faltan los usuarios base admin/martillero");

    const people = [
      ["51000001", "Valentina", "Rossi", "oro"],
      ["51000002", "Julián", "Paz", "platino"],
      ["51000003", "Camila", "Suárez", "plata"],
      ["51000004", "Tomás", "Lagos", "comun"],
      ["51000005", "Renata", "Ibarra", "oro"],
      ["51000006", "Bruno", "Méndez", "plata"],
      ["51000007", "Elena", "Ferrer", "platino"],
      ["51000008", "Nicolás", "Vega", "comun"],
    ];
    const clientIds = [];

    for (let i = 0; i < people.length; i += 1) {
      const [documento, nombre, apellido, categoria] = people[i];
      let userId = await scalar("SELECT identificador AS id FROM Users WHERE documento = @documento", { documento });
      if (!userId) {
        userId = await scalar(`
          INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, foto, fotoDniFrente, fotoDniDorso, clave)
          OUTPUT INSERTED.identificador AS id
          VALUES (@documento, @nombre, @apellido, @email, @telefono, @direccion, 'activo', @foto, @dniFrente, @dniDorso, '1234')
        `, {
          documento, nombre, apellido,
          email: `${nombre.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")}@demo.auctio.test`,
          telefono: `11510000${String(i + 1).padStart(2, "0")}`,
          direccion: `${DEMO_PREFIX}Av. del Libertador ${1200 + i * 75}, CABA`,
          foto: { type: sql.VarBinary(sql.MAX), value: images[i % images.length].bytes },
          dniFrente: { type: sql.VarBinary(sql.MAX), value: images[(i + 1) % images.length].bytes },
          dniDorso: { type: sql.VarBinary(sql.MAX), value: images[(i + 2) % images.length].bytes },
        });
      }
      clientIds.push(userId);

      await query(`
        IF NOT EXISTS (SELECT 1 FROM Clients WHERE identificador = @id)
          INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
          VALUES (@id, 32, 'si', @categoria, @adminId)
        ELSE
          UPDATE Clients SET admitido = 'si', categoria = @categoria, verificador = @adminId WHERE identificador = @id
      `, { id: userId, categoria, adminId });

      await query(`
        IF NOT EXISTS (SELECT 1 FROM Owners WHERE identificador = @id)
          INSERT INTO Owners (identificador, numeroPais, verificacionFinanciera, verificacionJudicial, calificacionRiesgo, verificador)
          VALUES (@id, 32, 'si', 'si', @riesgo, @adminId)
      `, { id: userId, riesgo: (i % 4) + 1, adminId });

      await query(`
        IF NOT EXISTS (SELECT 1 FROM PaymentMethods WHERE cliente = @id AND numeroReferencia = @reference)
          INSERT INTO PaymentMethods (cliente, tipo, entidad, numeroReferencia, esExtranjera, moneda, verificado, montoCheque, montoDisponible)
          VALUES (@id, @tipo, @entidad, @reference, @exterior, @moneda, @verificado, @montoCheque, @disponible)
      `, {
        id: userId,
        tipo: i % 3 === 0 ? "cuenta_bancaria" : i % 3 === 1 ? "tarjeta_credito" : "cheque_certificado",
        entidad: i % 2 === 0 ? "Banco Río Demo" : "Banco Internacional Demo",
        reference: `DEMO-XL-PAY-${documento}`,
        exterior: i % 4 === 1 ? "si" : "no",
        moneda: i % 4 === 1 ? "dolares" : "pesos",
        verificado: i === 7 ? "no" : "si",
        montoCheque: i % 3 === 2 ? 2500000 : null,
        disponible: i % 3 === 2 ? 2500000 : null,
      });

      await query(`
        IF NOT EXISTS (SELECT 1 FROM OwnerBankAccounts WHERE duenio = @id AND numeroCuenta = @account)
          INSERT INTO OwnerBankAccounts (duenio, banco, numeroCuenta, esExterior, moneda)
          VALUES (@id, 'Banco Patrimonial Demo', @account, 'no', 'pesos')
      `, { id: userId, account: `DEMO-XL-CTA-${documento}` });

      const policy = `DEMO-XL-POL-${documento}`;
      await query(`
        IF NOT EXISTS (SELECT 1 FROM Insurances WHERE nroPoliza = @policy)
          INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
          VALUES (@policy, @id, 'Aseguradora Patrimonial Demo', 'si', @amount)
      `, { policy, id: userId, amount: 750000 + i * 125000 });
    }

    const auctions = [
      ["Arte Contemporáneo", "en_curso", "comun", "pesos", 0, "20:30", 180],
      ["Alta Joyería", "abierta", "oro", "dolares", 2, "19:45", 90],
      ["Diseño del Siglo XX", "programada", "plata", "pesos", 7, "18:30", 120],
      ["Coleccionismo & Historia", "en_curso", "comun", "pesos", 0, "21:15", 150],
      ["Movilidad Clásica", "abierta", "platino", "dolares", 14, "17:00", 240],
      ["Música, Cavas & Juegos", "cerrada", "oro", "dolares", -7, "20:00", 120],
    ];
    const auctionIds = [];

    for (const [name, state, category, currency, dayOffset, time, duration] of auctions) {
      const location = `${DEMO_PREFIX}${name} · Palacio Auct.io`;
      let auctionId = await scalar("SELECT identificador AS id FROM Auctions WHERE ubicacion = @location", { location });
      if (!auctionId) {
        auctionId = await scalar(`
          INSERT INTO Auctions (fecha, hora, estado, subastador, ubicacion, capacidadAsistentes, tieneDeposito, seguridadPropia, categoria, moneda, duracionItemMinutos)
          OUTPUT INSERTED.identificador AS id
          VALUES (@date, @time, @state, @auctioneerId, @location, 180, 'si', 'si', @category, @currency, @duration)
        `, { date: daysFromToday(dayOffset), time, state, auctioneerId, location, category, currency, duration });
      } else {
        await query(`UPDATE Auctions SET fecha=@date, hora=@time, estado=@state, categoria=@category,
          moneda=@currency, duracionItemMinutos=@duration WHERE identificador=@id`,
        { date: daysFromToday(dayOffset), time, state, category, currency, duration, id: auctionId });
      }
      auctionIds.push(auctionId);
    }

    const productGroups = [
      ["Arte", ["Óleo geométrico azul y ocre", "Escultura ecuestre en bronce", "Acuarela de paisaje serrano", "Vaso de cerámica esmaltada"]],
      ["Joyas", ["Anillo Art Déco con diamantes", "Colgante con esmeralda colombiana", "Reloj mecánico de oro", "Collar de zafiros de Ceilán"]],
      ["Diseño", ["Aparador Art Déco en nogal", "Par de sillones escandinavos", "Mesa baja de mármol veteado", "Alfombra persa anudada a mano"]],
      ["Colección", ["Cámara telemétrica de 1954", "Brújula naval de latón", "Reloj de bolsillo esqueletado", "Globo terráqueo de escritorio"]],
      ["Movilidad", ["Coupé europeo de los años sesenta", "Roadster clásico restaurado", "Motocicleta de colección", "Scooter urbano de época"]],
      ["Lifestyle", ["Guitarra acústica de luthier", "Violín centroeuropeo con estuche", "Lote de vinos de guarda", "Ajedrez artesanal en nogal"]],
    ];
    const allProducts = [];

    for (let groupIndex = 0; groupIndex < productGroups.length; groupIndex += 1) {
      const [groupName, names] = productGroups[groupIndex];
      const auctionId = auctionIds[groupIndex];
      const catalogDescription = `${DEMO_PREFIX}Catálogo ${groupName}`;
      let catalogId = await scalar("SELECT identificador AS id FROM Catalogs WHERE descripcion = @description", { description: catalogDescription });
      if (!catalogId) {
        catalogId = await scalar(`INSERT INTO Catalogs (descripcion, subasta, responsable)
          OUTPUT INSERTED.identificador AS id VALUES (@description, @auctionId, @adminId)`,
        { description: catalogDescription, auctionId, adminId });
      }

      for (let itemIndex = 0; itemIndex < names.length; itemIndex += 1) {
        const shortDescription = `${DEMO_PREFIX}${names[itemIndex]}`;
        const ownerId = clientIds[(groupIndex + itemIndex) % clientIds.length];
        let productId = await scalar("SELECT identificador AS id FROM Products WHERE descripcionCatalogo = @description", { description: shortDescription });
        if (!productId) {
          productId = await scalar(`
            INSERT INTO Products (fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
              artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad, origenLicito,
              estadoAprobacion, revisor, duenio, precioBaseSugerido, precioBasePropuesto,
              comisionPropuesta, condicionesPropuestas, fechaPropuesta)
            OUTPUT INSERTED.identificador AS id
            VALUES (@date, 'si', @description, @fullDescription, @history, @artist, @objectDate,
              @storage, 'si', 'si', 'incluido_subasta', @adminId, @ownerId, @suggested,
              @basePrice, @commission, @conditions, GETDATE())
          `, {
            date: daysFromToday(-30 - itemIndex), description: shortDescription,
            fullDescription: `${names[itemIndex]}. Pieza seleccionada, revisada y fotografiada para la demostración integral de Auct.io.`,
            history: `Procedencia documentada. Colección privada · serie ${groupIndex + 1}.${itemIndex + 1}.`,
            artist: `${groupName} · autoría catalogada`, objectDate: daysFromToday(-3650 - groupIndex * 500),
            storage: `Depósito ${String.fromCharCode(65 + groupIndex)} · Rack ${itemIndex + 1}`,
            adminId, ownerId, suggested: 180000 + groupIndex * 210000 + itemIndex * 65000,
            basePrice: 200000 + groupIndex * 240000 + itemIndex * 75000,
            commission: 20000 + groupIndex * 24000 + itemIndex * 7500,
            conditions: "Inspección aprobada. Comisión 10%. Seguro y custodia incluidos hasta el cierre.",
          });
        }
        allProducts.push({ id: productId, groupIndex, itemIndex, ownerId, auctionId });

        const basePrice = 200000 + groupIndex * 240000 + itemIndex * 75000;
        const sold = groupIndex === 5 && itemIndex < 3 ? "si" : "no";
        let itemId = await scalar("SELECT identificador AS id FROM CatalogItems WHERE catalogo=@catalogId AND producto=@productId", { catalogId, productId });
        if (!itemId) {
          itemId = await scalar(`INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
            OUTPUT INSERTED.identificador AS id VALUES (@catalogId, @productId, @basePrice, @commission, @sold, @sold)`,
          { catalogId, productId, basePrice, commission: basePrice * 0.1, sold });
        }

        for (let order = 1; order <= 6; order += 1) {
          const image = images[(groupIndex + order - 1) % images.length].bytes;
          await query(`IF NOT EXISTS (SELECT 1 FROM Photos WHERE producto=@productId AND orden=@order)
            INSERT INTO Photos (producto, foto, orden) VALUES (@productId, @image, @order)`,
          { productId, order, image: { type: sql.VarBinary(sql.MAX), value: image } });
        }

        for (let bidderIndex = 0; bidderIndex < 3; bidderIndex += 1) {
          const bidderId = clientIds[(groupIndex + itemIndex + bidderIndex + 1) % clientIds.length];
          let attendeeId = await scalar("SELECT identificador AS id FROM Attendees WHERE cliente=@clientId AND subasta=@auctionId", { clientId: bidderId, auctionId });
          if (!attendeeId) {
            attendeeId = await scalar(`INSERT INTO Attendees (numeroPostor, cliente, subasta)
              OUTPUT INSERTED.identificador AS id VALUES (@number, @clientId, @auctionId)`,
            { number: 600 + groupIndex * 20 + bidderIndex, clientId: bidderId, auctionId });
          }
          const amount = basePrice + (bidderIndex + 1) * Math.max(1000, basePrice * 0.03);
          await query(`IF NOT EXISTS (SELECT 1 FROM Bids WHERE asistente=@attendeeId AND item=@itemId AND importe=@amount)
            INSERT INTO Bids (asistente, item, importe, ganador, confirmado)
            VALUES (@attendeeId, @itemId, @amount, @winner, 'si')`,
          { attendeeId, itemId, amount, winner: sold === "si" && bidderIndex === 2 ? "si" : "no" });
        }
      }
    }

    const workflowStates = [
      ["Consignación pendiente · reloj de sobremesa", "pendiente_inspeccion"],
      ["Consignación pendiente · bandeja de plata", "pendiente_inspeccion"],
      ["Propuesta enviada · tapiz modernista", "propuesta_enviada"],
      ["Propuesta enviada · pluma de colección", "propuesta_enviada"],
      ["Aceptada por cliente · escultura cinética", "aceptado_usuario"],
      ["Rechazada · reproducción sin certificado", "rechazado"],
    ];
    for (let i = 0; i < workflowStates.length; i += 1) {
      const [name, state] = workflowStates[i];
      const description = `${DEMO_PREFIX}${name}`;
      let productId = await scalar("SELECT identificador AS id FROM Products WHERE descripcionCatalogo=@description", { description });
      if (!productId) {
        productId = await scalar(`INSERT INTO Products (fecha, disponible, descripcionCatalogo, descripcionCompleta,
          historia, artistaDiseniador, ubicacionDeposito, declaracionPropiedad, origenLicito,
          estadoAprobacion, motivoRechazo, revisor, duenio, precioBaseSugerido, precioBasePropuesto,
          comisionPropuesta, condicionesPropuestas, fechaPropuesta)
          OUTPUT INSERTED.identificador AS id
          VALUES (@date, 'si', @description, @full, @history, 'Autoría en estudio', 'Recepción técnica',
            'si', 'si', @state, @rejection, @reviewer, @ownerId, @suggested, @proposed,
            @commission, @conditions, @proposalDate)`, {
          date: daysFromToday(-i), description,
          full: `${name}. Caso preparado para demostrar el circuito de revisión y decisión.`,
          history: "Documentación y procedencia incorporadas al legajo digital.", state,
          rejection: state === "rechazado" ? "Falta certificado de autenticidad verificable." : null,
          reviewer: state === "pendiente_inspeccion" ? null : adminId,
          ownerId: clientIds[i % clientIds.length], suggested: 90000 + i * 25000,
          proposed: state === "propuesta_enviada" || state === "aceptado_usuario" ? 110000 + i * 30000 : null,
          commission: state === "propuesta_enviada" || state === "aceptado_usuario" ? 11000 + i * 3000 : null,
          conditions: state === "propuesta_enviada" || state === "aceptado_usuario" ? "Comisión 10%. Custodia incluida." : null,
          proposalDate: state === "propuesta_enviada" || state === "aceptado_usuario" ? new Date() : null,
        });
      }
      for (let order = 1; order <= 6; order += 1) {
        const image = images[(i + order) % images.length].bytes;
        await query(`IF NOT EXISTS (SELECT 1 FROM Photos WHERE producto=@productId AND orden=@order)
          INSERT INTO Photos (producto, foto, orden) VALUES (@productId, @image, @order)`,
        { productId, order, image: { type: sql.VarBinary(sql.MAX), value: image } });
      }
    }

    const closedAuctionId = auctionIds[5];
    const soldProducts = allProducts.filter((product) => product.groupIndex === 5 && product.itemIndex < 3);
    for (let i = 0; i < soldProducts.length; i += 1) {
      const product = soldProducts[i];
      const buyerId = clientIds[(i + 2) % clientIds.length];
      const paymentMethodId = await scalar("SELECT TOP 1 identificador AS id FROM PaymentMethods WHERE cliente=@buyerId AND verificado='si' ORDER BY identificador", { buyerId });
      await query(`IF NOT EXISTS (SELECT 1 FROM AuctionRecords WHERE subasta=@auctionId AND producto=@productId)
        INSERT INTO AuctionRecords (subasta, duenio, producto, cliente, medioPago, importe, comision,
          costoEnvio, estadoPago, fechaVenta, retiroPersonal)
        VALUES (@auctionId, @ownerId, @productId, @buyerId, @paymentMethodId, @amount,
          @commission, @shipping, @paymentState, DATEADD(DAY, -6, GETDATE()), @pickup)`, {
        auctionId: closedAuctionId, ownerId: product.ownerId, productId: product.id, buyerId,
        paymentMethodId, amount: 980000 + i * 165000, commission: 98000 + i * 16500,
        shipping: i === 1 ? 35000 : 0, paymentState: i === 2 ? "pendiente" : "pagado",
        pickup: i === 1 ? "no" : "si",
      });
    }

    for (let i = 0; i < 3; i += 1) {
      const clientId = clientIds[5 + i];
      await query(`IF NOT EXISTS (SELECT 1 FROM Fines WHERE cliente=@clientId AND subasta=@auctionId AND monto=@amount)
        INSERT INTO Fines (cliente, subasta, monto, pagada, fechaGeneracion)
        VALUES (@clientId, @auctionId, @amount, @paid, DATEADD(DAY, -@age, GETDATE()))`,
      { clientId, auctionId: closedAuctionId, amount: 65000 + i * 25000, paid: i === 0 ? "si" : "no", age: i + 1 });
    }

    const notifications = [
      ["Bienvenido a la demo premium", "Tu perfil está verificado y listo para operar."],
      ["Nueva subasta disponible", "Ya podés consultar el catálogo, los precios base y la fecha de apertura."],
      ["Consignación actualizada", "La empresa incorporó novedades al legajo de uno de tus bienes."],
      ["Recordatorio de seguridad", "Revisá medios de pago, límites y condiciones antes de confirmar una puja."],
    ];
    for (let clientIndex = 0; clientIndex < clientIds.length; clientIndex += 1) {
      for (let notificationIndex = 0; notificationIndex < notifications.length; notificationIndex += 1) {
        const [title, message] = notifications[notificationIndex];
        const fullTitle = `${DEMO_PREFIX}${title}`;
        await query(`IF NOT EXISTS (SELECT 1 FROM Notifications WHERE cliente=@clientId AND titulo=@title)
          INSERT INTO Notifications (cliente, titulo, mensaje, fechaHora, leida)
          VALUES (@clientId, @title, @message, DATEADD(HOUR, -@age, GETDATE()), @read)`, {
          clientId: clientIds[clientIndex], title: fullTitle, message,
          age: clientIndex * 4 + notificationIndex, read: notificationIndex === 0 ? "si" : "no",
        });
      }
    }

    await transaction.commit();

    const summary = await pool.request().query(`
      SELECT
        (SELECT COUNT(*) FROM Users WHERE direccion LIKE '${DEMO_PREFIX}%') AS usuariosDemo,
        (SELECT COUNT(*) FROM Auctions WHERE ubicacion LIKE '${DEMO_PREFIX}%') AS subastasDemo,
        (SELECT COUNT(*) FROM Products WHERE descripcionCatalogo LIKE '${DEMO_PREFIX}%') AS productosDemo,
        (SELECT COUNT(*) FROM Photos ph INNER JOIN Products p ON p.identificador=ph.producto WHERE p.descripcionCatalogo LIKE '${DEMO_PREFIX}%') AS fotosDemo,
        (SELECT COUNT(*) FROM CatalogItems ci INNER JOIN Products p ON p.identificador=ci.producto WHERE p.descripcionCatalogo LIKE '${DEMO_PREFIX}%') AS lotesDemo,
        (SELECT COUNT(*) FROM Notifications WHERE titulo LIKE '${DEMO_PREFIX}%') AS avisosDemo
    `);
    console.log(JSON.stringify(summary.recordset[0]));
  } catch (error) {
    if (transaction._aborted !== true) await transaction.rollback();
    throw error;
  } finally {
    await pool.close();
  }
}

seed().catch((error) => {
  console.error(`Seed DEMO XL falló: ${error.message}`);
  process.exitCode = 1;
});
