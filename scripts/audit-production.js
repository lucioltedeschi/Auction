const { poolPromise } = require("../backend/db");

(async () => {
  const pool = await poolPromise;
  const summary = await pool.request().query(`
    SELECT DATEADD(HOUR,-3,SYSUTCDATETIME()) AS horaArgentina,
           SYSUTCDATETIME() AS horaUtc,
           (SELECT COUNT(*) FROM Users) AS usuarios,
           (SELECT COUNT(*) FROM Auctions) AS subastas,
           (SELECT COUNT(*) FROM Auctions WHERE estado IN ('programada','abierta','en_curso')) AS subastasActivas,
           (SELECT COUNT(*) FROM CatalogItems WHERE vendido='no') AS lotesAbiertos,
           (SELECT COUNT(*) FROM Clients c INNER JOIN Owners o ON o.identificador=c.identificador
             INNER JOIN Users u ON u.identificador=c.identificador
             WHERE c.identificador=9000007 AND c.admitido='si' AND u.estado='activo') AS clienteEmpresa;

    SELECT a.identificador AS subastaId, a.fecha, a.hora, a.estado, a.ubicacion,
           (SELECT COUNT(*) FROM CatalogItems ci INNER JOIN Catalogs c ON c.identificador=ci.catalogo WHERE c.subasta=a.identificador) AS lotes,
           (SELECT COUNT(*) FROM CatalogItems ci INNER JOIN Catalogs c ON c.identificador=ci.catalogo WHERE c.subasta=a.identificador AND ci.vendido='no') AS lotesAbiertos,
           (SELECT COUNT(*) FROM AuctionRecords ar WHERE ar.subasta=a.identificador) AS ventas
    FROM Auctions a
    ORDER BY a.fecha DESC,a.hora DESC;

    SELECT ci.identificador AS itemId, c.subasta AS subastaId, p.descripcionCatalogo
    FROM CatalogItems ci
    INNER JOIN Catalogs c ON c.identificador=ci.catalogo
    INNER JOIN Products p ON p.identificador=ci.producto
    WHERE ci.vendido='si'
      AND NOT EXISTS (SELECT 1 FROM AuctionRecords ar WHERE ar.subasta=c.subasta AND ar.producto=ci.producto);

    SELECT t.name AS tabla, c.name AS columna, dc.definition
    FROM sys.default_constraints dc
    INNER JOIN sys.columns c ON c.default_object_id=dc.object_id
    INNER JOIN sys.tables t ON t.object_id=c.object_id
    WHERE c.name IN ('fechaAlta','fechaHora','fechaVenta','fechaIngreso','fechaGeneracion')
    ORDER BY t.name,c.name;
  `);
  const [general, auctions, soldWithoutRecord, defaults] = summary.recordsets;
  const inconsistencias = {
    subastasCerradasConLotesAbiertos: auctions.filter((a) => a.estado === "cerrada" && Number(a.lotesAbiertos) > 0),
    subastasActivasSinLotesAbiertos: auctions.filter((a) => ["programada", "abierta", "en_curso"].includes(a.estado) && Number(a.lotes) > 0 && Number(a.lotesAbiertos) === 0),
    lotesVendidosSinVenta: soldWithoutRecord,
    defaultsFueraGMT3: defaults.filter((d) => !String(d.definition).includes("-3")),
  };
  console.log(JSON.stringify({ general: general[0], subastas: auctions, inconsistencias, defaults }, null, 2));
  const critical = Number(general[0].clienteEmpresa) !== 1
    || inconsistencias.subastasCerradasConLotesAbiertos.length
    || inconsistencias.subastasActivasSinLotesAbiertos.length
    || inconsistencias.lotesVendidosSinVenta.length
    || inconsistencias.defaultsFueraGMT3.length;
  await pool.close();
  if (critical) process.exitCode = 2;
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
