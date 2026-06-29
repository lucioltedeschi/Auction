const { poolPromise, sql } = require("../backend/db");
const COMPANY_CLIENT_ID = Number(process.env.COMPANY_CLIENT_ID || 9000007);

(async () => {
  const pool = await poolPromise;
  const transaction = new sql.Transaction(pool);
  await transaction.begin(sql.ISOLATION_LEVEL.SERIALIZABLE);
  try {
    const orphaned = await new sql.Request(transaction).query(`
      SELECT ci.identificador AS itemId, c.subasta, ci.producto, ci.precioBase, ci.comision,
             p.duenio, a.moneda
      FROM CatalogItems ci
      INNER JOIN Catalogs c ON c.identificador=ci.catalogo
      INNER JOIN Products p ON p.identificador=ci.producto
      INNER JOIN Auctions a ON a.identificador=c.subasta
      WHERE ci.vendido='si'
        AND NOT EXISTS (SELECT 1 FROM AuctionRecords ar WHERE ar.subasta=c.subasta AND ar.producto=ci.producto)
    `);
    const repaired = [];
    for (const item of orphaned.recordset) {
      const winner = await new sql.Request(transaction).input("item", sql.Int, item.itemId).query(`
        SELECT TOP 1 b.identificador AS bidId,b.importe,at.cliente
        FROM Bids b INNER JOIN Attendees at ON at.identificador=b.asistente
        WHERE b.item=@item ORDER BY b.importe DESC,b.fechaHora ASC,b.identificador ASC
      `);
      const winning = winner.recordset[0];
      const buyer = winning ? Number(winning.cliente) : COMPANY_CLIENT_ID;
      const amount = winning ? Number(winning.importe) : Number(item.precioBase);
      if (winning) {
        await new sql.Request(transaction).input("item", sql.Int, item.itemId)
          .query("UPDATE Bids SET ganador='no' WHERE item=@item");
        await new sql.Request(transaction).input("bid", sql.Int, winning.bidId)
          .query("UPDATE Bids SET ganador='si' WHERE identificador=@bid");
      }
      await new sql.Request(transaction)
        .input("auction", sql.Int, item.subasta).input("owner", sql.Int, item.duenio)
        .input("product", sql.Int, item.producto).input("buyer", sql.Int, buyer)
        .input("amount", sql.Decimal(18,2), amount)
        .input("commission", sql.Decimal(18,2), Math.max(Number(item.comision),0.02))
        .input("state", sql.VarChar(20), winning ? "pendiente" : "pagado")
        .query(`INSERT INTO AuctionRecords (subasta,duenio,producto,cliente,medioPago,importe,comision,costoEnvio,estadoPago,fechaVenta,retiroPersonal)
                VALUES (@auction,@owner,@product,@buyer,NULL,@amount,@commission,0,@state,DATEADD(HOUR,-3,SYSUTCDATETIME()),'no')`);
      await new sql.Request(transaction).input("product", sql.Int, item.producto).input("buyer", sql.Int, buyer)
        .query("UPDATE Products SET duenio=@buyer,disponible='no' WHERE identificador=@product");
      repaired.push({ itemId: item.itemId, auctionId: item.subasta, buyer, amount });
    }

    const reopened = await new sql.Request(transaction).query(`
      UPDATE Auctions SET estado='en_curso'
      OUTPUT INSERTED.identificador AS auctionId
      WHERE estado='cerrada' AND EXISTS (
        SELECT 1 FROM Catalogs c INNER JOIN CatalogItems ci ON ci.catalogo=c.identificador
        WHERE c.subasta=Auctions.identificador AND ci.vendido='no'
      )
    `);
    const closed = await new sql.Request(transaction).query(`
      UPDATE Auctions SET estado='cerrada'
      OUTPUT INSERTED.identificador AS auctionId
      WHERE estado IN ('programada','abierta','en_curso')
        AND EXISTS (SELECT 1 FROM Catalogs c INNER JOIN CatalogItems ci ON ci.catalogo=c.identificador WHERE c.subasta=Auctions.identificador)
        AND NOT EXISTS (SELECT 1 FROM Catalogs c INNER JOIN CatalogItems ci ON ci.catalogo=c.identificador WHERE c.subasta=Auctions.identificador AND ci.vendido='no')
    `);
    await transaction.commit();
    console.log(JSON.stringify({ repairedSoldWithoutSale: repaired, reopenedForReconciliation: reopened.recordset, closedCompleted: closed.recordset }, null, 2));
  } catch (error) {
    if (!transaction._aborted) await transaction.rollback().catch(() => {});
    throw error;
  }
  await pool.close();
})().catch((error) => {
  console.error("No se pudo reparar la consistencia:", error.message);
  process.exit(1);
});
