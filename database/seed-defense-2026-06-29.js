const fs = require("fs");
const path = require("path");
const { poolPromise, sql } = require("../backend/db");

const DATE = "2026-06-29";
const PHOTO_FILES = [
  "arte-showroom.jpg", "coleccion-showroom.jpg", "diseno-showroom.jpg",
  "joyas-showroom.jpg", "lifestyle-showroom.jpg", "vehiculos-showroom.jpg",
];
const EVENTS = [
  {
    time: "18:00", duration: 120, location: "[DEFENSA 29/06] Arte y coleccion - Buenos Aires",
    category: "comun", currency: "pesos",
    lots: [
      ["Defensa - Oleo contemporaneo patagonico", "Obra original con certificado, marco de madera y seis vistas de conservacion.", 180000],
      ["Defensa - Camara fotografica de coleccion", "Equipo mecanico restaurado, estuche original y documentacion historica.", 95000],
    ],
  },
  {
    time: "19:30", duration: 120, location: "[DEFENSA 29/06] Diseno y joyas - Salon Auct.io",
    category: "plata", currency: "pesos",
    lots: [
      ["Defensa - Sillon de autor edicion numerada", "Pieza de diseno argentino, tapizado restaurado y procedencia documentada.", 320000],
      ["Defensa - Reloj suizo automatico", "Reloj de acero con servicio reciente, caja y documentacion de origen.", 450000],
    ],
  },
  {
    time: "21:00", duration: 90, location: "[DEFENSA 29/06] Coleccion premium - Sala Palermo",
    category: "oro", currency: "dolares",
    lots: [
      ["Defensa - Escultura de bronce firmada", "Escultura numerada con certificado de autenticidad y poliza de seguro.", 2400],
      ["Defensa - Anillo de platino y diamantes", "Joya certificada con detalle de peso, pureza y seis fotografias macro.", 3800],
    ],
  },
];

(async () => {
  const pool = await poolPromise;
  const refs = await pool.request().query(`
    SELECT TOP 1 identificador AS auctioneer FROM Auctioneers ORDER BY identificador;
    SELECT TOP 1 identificador AS employee FROM Employees ORDER BY identificador;
    SELECT TOP 1 c.identificador AS owner FROM Clients c
      INNER JOIN Owners o ON o.identificador=c.identificador
      INNER JOIN Users u ON u.identificador=c.identificador
      WHERE c.admitido='si' AND u.estado='activo' AND c.identificador<>9000007
      ORDER BY c.identificador;
    SELECT COUNT(*) AS companyOk FROM Clients c INNER JOIN Owners o ON o.identificador=c.identificador
      INNER JOIN Users u ON u.identificador=c.identificador
      WHERE c.identificador=9000007 AND c.admitido='si' AND u.estado='activo';
  `);
  const auctioneer = refs.recordsets[0][0]?.auctioneer;
  const employee = refs.recordsets[1][0]?.employee;
  const owner = refs.recordsets[2][0]?.owner;
  if (!auctioneer || !employee || !owner || Number(refs.recordsets[3][0]?.companyOk) !== 1) {
    throw new Error("Faltan martillero, empleado, propietario demo o cliente empresa 9000007");
  }
  const photos = PHOTO_FILES.map((name) => fs.readFileSync(path.join(__dirname, "demo-assets", name)));
  const report = [];

  for (const event of EVENTS) {
    const transaction = new sql.Transaction(pool);
    await transaction.begin(sql.ISOLATION_LEVEL.SERIALIZABLE);
    try {
      let auction = await new sql.Request(transaction)
        .input("location", sql.VarChar(350), event.location)
        .query("SELECT identificador FROM Auctions WHERE ubicacion=@location");
      let auctionId;
      if (auction.recordset.length) {
        auctionId = auction.recordset[0].identificador;
        const activity = await new sql.Request(transaction).input("id", sql.Int, auctionId).query(`
          SELECT (SELECT COUNT(*) FROM Bids b INNER JOIN CatalogItems ci ON ci.identificador=b.item
                   INNER JOIN Catalogs c ON c.identificador=ci.catalogo WHERE c.subasta=@id) AS bids,
                 (SELECT COUNT(*) FROM AuctionRecords WHERE subasta=@id) AS sales
        `);
        if (Number(activity.recordset[0].bids) || Number(activity.recordset[0].sales)) {
          throw new Error(`La subasta demo ${auctionId} ya tiene actividad y no puede reiniciarse`);
        }
        await new sql.Request(transaction).input("id", sql.Int, auctionId)
          .input("date", sql.Date, DATE).input("time", sql.VarChar(5), event.time)
          .input("duration", sql.Int, event.duration).input("category", sql.VarChar(10), event.category)
          .input("currency", sql.VarChar(10), event.currency)
          .query("UPDATE Auctions SET fecha=@date,hora=@time,estado='programada',duracionItemMinutos=@duration,categoria=@category,moneda=@currency WHERE identificador=@id");
      } else {
        auction = await new sql.Request(transaction)
          .input("date", sql.Date, DATE).input("time", sql.VarChar(5), event.time)
          .input("auctioneer", sql.Int, auctioneer).input("location", sql.VarChar(350), event.location)
          .input("duration", sql.Int, event.duration).input("category", sql.VarChar(10), event.category)
          .input("currency", sql.VarChar(10), event.currency)
          .query(`INSERT INTO Auctions (fecha,hora,estado,subastador,ubicacion,capacidadAsistentes,tieneDeposito,seguridadPropia,categoria,moneda,duracionItemMinutos)
                  OUTPUT INSERTED.identificador VALUES (@date,@time,'programada',@auctioneer,@location,180,'si','si',@category,@currency,@duration)`);
        auctionId = auction.recordset[0].identificador;
      }
      let catalog = await new sql.Request(transaction).input("auction", sql.Int, auctionId)
        .query("SELECT TOP 1 identificador FROM Catalogs WHERE subasta=@auction ORDER BY identificador");
      let catalogId = catalog.recordset[0]?.identificador;
      if (!catalogId) {
        catalog = await new sql.Request(transaction).input("auction", sql.Int, auctionId)
          .input("employee", sql.Int, employee).input("description", sql.VarChar(250), `Catalogo defensa ${event.time}`)
          .query("INSERT INTO Catalogs (descripcion,subasta,responsable) OUTPUT INSERTED.identificador VALUES (@description,@auction,@employee)");
        catalogId = catalog.recordset[0].identificador;
      }

      const itemIds = [];
      for (const [name, description, price] of event.lots) {
        let product = await new sql.Request(transaction).input("name", sql.VarChar(500), name)
          .query("SELECT identificador FROM Products WHERE descripcionCatalogo=@name");
        let productId = product.recordset[0]?.identificador;
        if (!productId) {
          product = await new sql.Request(transaction)
            .input("name", sql.VarChar(500), name).input("description", sql.VarChar(500), description)
            .input("date", sql.Date, DATE)
            .input("owner", sql.Int, owner).input("price", sql.Decimal(18,2), price)
            .input("employee", sql.Int, employee)
            .query(`INSERT INTO Products (fecha,disponible,descripcionCatalogo,descripcionCompleta,historia,ubicacionDeposito,declaracionPropiedad,origenLicito,estadoAprobacion,revisor,duenio,seguro,precioBasePropuesto,comisionPropuesta,condicionesPropuestas,fechaPropuesta,precioBaseSugerido)
                    OUTPUT INSERTED.identificador VALUES (@date,'si',@name,@description,'Seleccion curatorial preparada para la defensa del 29/06/2026.','Deposito central Auct.io','si','si','incluido_subasta',@employee,@owner,NULL,@price,0.10,'Comision 10%, catalogacion y trazabilidad incluidos.',DATEADD(HOUR,-3,SYSUTCDATETIME()),@price)`);
          productId = product.recordset[0].identificador;
          for (let i = 0; i < photos.length; i += 1) {
            await new sql.Request(transaction).input("product", sql.Int, productId)
              .input("photo", sql.VarBinary(sql.MAX), photos[i]).input("order", sql.Int, i + 1)
              .query("INSERT INTO Photos (producto,foto,orden) VALUES (@product,@photo,@order)");
          }
        }
        let item = await new sql.Request(transaction).input("catalog", sql.Int, catalogId)
          .input("product", sql.Int, productId)
          .query("SELECT identificador FROM CatalogItems WHERE catalogo=@catalog AND producto=@product");
        if (!item.recordset.length) {
          item = await new sql.Request(transaction).input("catalog", sql.Int, catalogId)
            .input("product", sql.Int, productId).input("price", sql.Decimal(18,2), price)
            .query("INSERT INTO CatalogItems (catalogo,producto,precioBase,comision,subastado,vendido) OUTPUT INSERTED.identificador VALUES (@catalog,@product,@price,0.10,'no','no')");
        } else {
          await new sql.Request(transaction).input("item", sql.Int, item.recordset[0].identificador)
            .input("price", sql.Decimal(18,2), price)
            .query("UPDATE CatalogItems SET precioBase=@price,comision=0.10,subastado='no',vendido='no' WHERE identificador=@item");
        }
        itemIds.push(item.recordset[0].identificador);
      }
      await transaction.commit();
      report.push({ auctionId, date: DATE, time: event.time, duration: event.duration, location: event.location, itemIds });
    } catch (error) {
      if (!transaction._aborted) await transaction.rollback().catch(() => {});
      throw error;
    }
  }
  console.log(JSON.stringify({ timezone: "America/Argentina/Buenos_Aires (GMT-3)", auctions: report }, null, 2));
  await pool.close();
})().catch((error) => {
  console.error("No se pudo preparar la defensa:", error.message);
  process.exit(1);
});
