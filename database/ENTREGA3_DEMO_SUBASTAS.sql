USE auction;
GO

SET NOCOUNT ON;

/* Seed recomendado para Entrega 3.
   Es idempotente: se puede volver a ejecutar sin duplicar subastas,
   catalogos, lotes, asistentes ni fotos demo. */

DECLARE @empleadoId INT;
DECLARE @subastadorId INT;
DECLARE @alejandroId INT;
DECLARE @martinId INT;
DECLARE @sofiaId INT;

SELECT @empleadoId = identificador FROM Users WHERE documento = '20000111';
SELECT @subastadorId = identificador FROM Users WHERE documento = '20000222';
SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId = identificador FROM Users WHERE documento = '31888777';

IF @empleadoId IS NULL OR @subastadorId IS NULL OR @alejandroId IS NULL OR @martinId IS NULL OR @sofiaId IS NULL
BEGIN
    RAISERROR('Ejecutar DATOS_DEMO_BASE.sql antes de ENTREGA3_DEMO_SUBASTAS.sql.', 16, 1);
    RETURN;
END

INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
SELECT 'DEMO-ALE-003', @alejandroId, 'Zurich Argentina', 'si', 350000.00
WHERE NOT EXISTS (SELECT 1 FROM Insurances WHERE nroPoliza = 'DEMO-ALE-003');

INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
SELECT 'DEMO-MAR-003', @martinId, 'BBVA Seguros', 'no', 420000.00
WHERE NOT EXISTS (SELECT 1 FROM Insurances WHERE nroPoliza = 'DEMO-MAR-003');

INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
SELECT 'DEMO-SOF-003', @sofiaId, 'Federacion Patronal', 'si', 280000.00
WHERE NOT EXISTS (SELECT 1 FROM Insurances WHERE nroPoliza = 'DEMO-SOF-003');

INSERT INTO Auctions (fecha, hora, estado, subastador, ubicacion, capacidadAsistentes, tieneDeposito, seguridadPropia, categoria, moneda, duracionItemMinutos)
SELECT CAST(GETDATE() AS DATE), '21:02', 'en_curso', @subastadorId, 'Demo Entrega 3 - Sala Palermo', 120, 'si', 'si', 'comun', 'pesos', 3
WHERE NOT EXISTS (SELECT 1 FROM Auctions WHERE ubicacion = 'Demo Entrega 3 - Sala Palermo');

INSERT INTO Auctions (fecha, hora, estado, subastador, ubicacion, capacidadAsistentes, tieneDeposito, seguridadPropia, categoria, moneda, duracionItemMinutos)
SELECT DATEADD(DAY, 7, CAST(GETDATE() AS DATE)), '21:10', 'abierta', @subastadorId, 'Demo Entrega 3 - Salon VIP', 60, 'si', 'si', 'oro', 'dolares', 3
WHERE NOT EXISTS (SELECT 1 FROM Auctions WHERE ubicacion = 'Demo Entrega 3 - Salon VIP');

INSERT INTO Auctions (fecha, hora, estado, subastador, ubicacion, capacidadAsistentes, tieneDeposito, seguridadPropia, categoria, moneda, duracionItemMinutos)
SELECT DATEADD(DAY, 21, CAST(GETDATE() AS DATE)), '21:10', 'programada', @subastadorId, 'Demo Entrega 3 - Rosario', 90, 'si', 'no', 'plata', 'pesos', 3
WHERE NOT EXISTS (SELECT 1 FROM Auctions WHERE ubicacion = 'Demo Entrega 3 - Rosario');

UPDATE Auctions
SET fecha = CAST(GETDATE() AS DATE),
    hora = '21:02',
    estado = 'en_curso'
WHERE ubicacion = 'Demo Entrega 3 - Sala Palermo';

UPDATE Auctions
SET fecha = DATEADD(DAY, 7, CAST(GETDATE() AS DATE)),
    hora = '21:10',
    estado = 'abierta'
WHERE ubicacion = 'Demo Entrega 3 - Salon VIP';

UPDATE Auctions
SET fecha = DATEADD(DAY, 21, CAST(GETDATE() AS DATE)),
    hora = '21:10',
    estado = 'programada'
WHERE ubicacion = 'Demo Entrega 3 - Rosario';

DECLARE @subArteId INT;
DECLARE @subJoyaId INT;
DECLARE @subMuebId INT;

SELECT @subArteId = identificador FROM Auctions WHERE ubicacion = 'Demo Entrega 3 - Sala Palermo';
SELECT @subJoyaId = identificador FROM Auctions WHERE ubicacion = 'Demo Entrega 3 - Salon VIP';
SELECT @subMuebId = identificador FROM Auctions WHERE ubicacion = 'Demo Entrega 3 - Rosario';

INSERT INTO Products (
    fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
    artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad,
    origenLicito, estadoAprobacion, revisor, duenio, seguro, precioBaseSugerido
)
SELECT CAST(GETDATE() AS DATE), 'si', 'Demo E3 - Escultura de bronce numerada',
       'Escultura de bronce fundido con base de marmol negro. Pieza numerada para catalogo demo.',
       'Adquirida en galeria local y conservada en deposito climatizado.',
       'R. Haber', '2003-09-15', 'Deposito A - Estante 7',
       'si', 'si', 'incluido_subasta', @empleadoId, @alejandroId, 'DEMO-ALE-003', 45000.00
WHERE NOT EXISTS (SELECT 1 FROM Products WHERE descripcionCatalogo = 'Demo E3 - Escultura de bronce numerada');

INSERT INTO Products (
    fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
    artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad,
    origenLicito, estadoAprobacion, revisor, duenio, seguro, precioBaseSugerido
)
SELECT CAST(GETDATE() AS DATE), 'si', 'Demo E3 - Oleo paisaje pampeano',
       'Oleo sobre tela de gran formato con marco original de madera tallada.',
       'Pieza heredada de coleccion familiar con valuacion pericial previa.',
       'Escuela rioplatense', '1940-01-01', 'Deposito B - Sala Pintura 2',
       'si', 'si', 'incluido_subasta', @empleadoId, @alejandroId, 'DEMO-ALE-003', 85000.00
WHERE NOT EXISTS (SELECT 1 FROM Products WHERE descripcionCatalogo = 'Demo E3 - Oleo paisaje pampeano');

INSERT INTO Products (
    fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
    artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad,
    origenLicito, estadoAprobacion, revisor, duenio, seguro, precioBaseSugerido
)
SELECT CAST(GETDATE() AS DATE), 'si', 'Demo E3 - Reloj acero y oro',
       'Reloj automatico de caballero con caja, papeles y service documentado.',
       'Uso esporadico y conservacion en caja fuerte desde 2018.',
       'Rolex SA', '2001-06-10', 'Caja Fuerte B - Relojes',
       'si', 'si', 'incluido_subasta', @empleadoId, @martinId, 'DEMO-MAR-003', 12000.00
WHERE NOT EXISTS (SELECT 1 FROM Products WHERE descripcionCatalogo = 'Demo E3 - Reloj acero y oro');

INSERT INTO Products (
    fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
    artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad,
    origenLicito, estadoAprobacion, revisor, duenio, seguro, precioBaseSugerido
)
SELECT CAST(GETDATE() AS DATE), 'si', 'Demo E3 - Anillo platino diamantes',
       'Anillo art deco en platino con diamante central certificado y diamantes laterales.',
       'Joya de herencia familiar con certificado gemologico.',
       NULL, '1935-01-01', 'Caja Fuerte B - Joyeria',
       'si', 'si', 'incluido_subasta', @empleadoId, @martinId, 'DEMO-MAR-003', 9500.00
WHERE NOT EXISTS (SELECT 1 FROM Products WHERE descripcionCatalogo = 'Demo E3 - Anillo platino diamantes');

INSERT INTO Products (
    fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
    artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad,
    origenLicito, estadoAprobacion, revisor, duenio, seguro, precioBaseSugerido
)
SELECT CAST(GETDATE() AS DATE), 'si', 'Demo E3 - Sillon Barcelona original',
       'Sillon Barcelona fabricado por Knoll International, estructura en acero y cuero negro.',
       'Adquirido en liquidacion de oficina de arquitectura.',
       'Mies van der Rohe', '1985-01-01', 'Deposito D - Muebles Diseno',
       'si', 'si', 'incluido_subasta', @empleadoId, @sofiaId, 'DEMO-SOF-003', 95000.00
WHERE NOT EXISTS (SELECT 1 FROM Products WHERE descripcionCatalogo = 'Demo E3 - Sillon Barcelona original');

INSERT INTO Products (
    fecha, disponible, descripcionCatalogo, descripcionCompleta, historia,
    artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad,
    origenLicito, estadoAprobacion, revisor, duenio, seguro, precioBaseSugerido
)
SELECT CAST(GETDATE() AS DATE), 'si', 'Demo E3 - Lampara Arco Flos',
       'Lampara Arco original de Flos con base de marmol de Carrara y pantalla de aluminio.',
       'Importada de Italia con documentacion de compra.',
       'Achille Castiglioni', '1994-01-01', 'Deposito E - Iluminacion',
       'si', 'si', 'incluido_subasta', @empleadoId, @sofiaId, 'DEMO-SOF-003', 55000.00
WHERE NOT EXISTS (SELECT 1 FROM Products WHERE descripcionCatalogo = 'Demo E3 - Lampara Arco Flos');

DECLARE @catArteId INT;
DECLARE @catJoyaId INT;
DECLARE @catMuebId INT;

INSERT INTO Catalogs (descripcion, subasta, responsable)
SELECT 'Demo Entrega 3 - Arte y antiguedades', @subArteId, @empleadoId
WHERE NOT EXISTS (SELECT 1 FROM Catalogs WHERE subasta = @subArteId AND descripcion = 'Demo Entrega 3 - Arte y antiguedades');

INSERT INTO Catalogs (descripcion, subasta, responsable)
SELECT 'Demo Entrega 3 - Joyeria y relojes', @subJoyaId, @empleadoId
WHERE NOT EXISTS (SELECT 1 FROM Catalogs WHERE subasta = @subJoyaId AND descripcion = 'Demo Entrega 3 - Joyeria y relojes');

INSERT INTO Catalogs (descripcion, subasta, responsable)
SELECT 'Demo Entrega 3 - Muebles y diseno', @subMuebId, @empleadoId
WHERE NOT EXISTS (SELECT 1 FROM Catalogs WHERE subasta = @subMuebId AND descripcion = 'Demo Entrega 3 - Muebles y diseno');

SELECT @catArteId = identificador FROM Catalogs WHERE subasta = @subArteId AND descripcion = 'Demo Entrega 3 - Arte y antiguedades';
SELECT @catJoyaId = identificador FROM Catalogs WHERE subasta = @subJoyaId AND descripcion = 'Demo Entrega 3 - Joyeria y relojes';
SELECT @catMuebId = identificador FROM Catalogs WHERE subasta = @subMuebId AND descripcion = 'Demo Entrega 3 - Muebles y diseno';

DECLARE @pEscultura INT; SELECT @pEscultura = identificador FROM Products WHERE descripcionCatalogo = 'Demo E3 - Escultura de bronce numerada';
DECLARE @pOleo INT; SELECT @pOleo = identificador FROM Products WHERE descripcionCatalogo = 'Demo E3 - Oleo paisaje pampeano';
DECLARE @pReloj INT; SELECT @pReloj = identificador FROM Products WHERE descripcionCatalogo = 'Demo E3 - Reloj acero y oro';
DECLARE @pAnillo INT; SELECT @pAnillo = identificador FROM Products WHERE descripcionCatalogo = 'Demo E3 - Anillo platino diamantes';
DECLARE @pSillon INT; SELECT @pSillon = identificador FROM Products WHERE descripcionCatalogo = 'Demo E3 - Sillon Barcelona original';
DECLARE @pLampara INT; SELECT @pLampara = identificador FROM Products WHERE descripcionCatalogo = 'Demo E3 - Lampara Arco Flos';

INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
SELECT @catArteId, @pEscultura, 45000.00, 4500.00, 'no', 'no'
WHERE NOT EXISTS (SELECT 1 FROM CatalogItems WHERE catalogo = @catArteId AND producto = @pEscultura);

INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
SELECT @catArteId, @pOleo, 85000.00, 8500.00, 'no', 'no'
WHERE NOT EXISTS (SELECT 1 FROM CatalogItems WHERE catalogo = @catArteId AND producto = @pOleo);

INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
SELECT @catJoyaId, @pReloj, 12000.00, 1200.00, 'no', 'no'
WHERE NOT EXISTS (SELECT 1 FROM CatalogItems WHERE catalogo = @catJoyaId AND producto = @pReloj);

INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
SELECT @catJoyaId, @pAnillo, 9500.00, 950.00, 'no', 'no'
WHERE NOT EXISTS (SELECT 1 FROM CatalogItems WHERE catalogo = @catJoyaId AND producto = @pAnillo);

INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
SELECT @catMuebId, @pSillon, 95000.00, 9500.00, 'no', 'no'
WHERE NOT EXISTS (SELECT 1 FROM CatalogItems WHERE catalogo = @catMuebId AND producto = @pSillon);

INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
SELECT @catMuebId, @pLampara, 55000.00, 5500.00, 'no', 'no'
WHERE NOT EXISTS (SELECT 1 FROM CatalogItems WHERE catalogo = @catMuebId AND producto = @pLampara);

INSERT INTO Attendees (numeroPostor, cliente, subasta)
SELECT 301, @alejandroId, @subArteId
WHERE NOT EXISTS (SELECT 1 FROM Attendees WHERE cliente = @alejandroId AND subasta = @subArteId);

INSERT INTO Attendees (numeroPostor, cliente, subasta)
SELECT 302, @martinId, @subArteId
WHERE NOT EXISTS (SELECT 1 FROM Attendees WHERE cliente = @martinId AND subasta = @subArteId);

INSERT INTO Attendees (numeroPostor, cliente, subasta)
SELECT 303, @sofiaId, @subArteId
WHERE NOT EXISTS (SELECT 1 FROM Attendees WHERE cliente = @sofiaId AND subasta = @subArteId);

INSERT INTO Attendees (numeroPostor, cliente, subasta)
SELECT 401, @alejandroId, @subJoyaId
WHERE NOT EXISTS (SELECT 1 FROM Attendees WHERE cliente = @alejandroId AND subasta = @subJoyaId);

INSERT INTO Attendees (numeroPostor, cliente, subasta)
SELECT 402, @martinId, @subJoyaId
WHERE NOT EXISTS (SELECT 1 FROM Attendees WHERE cliente = @martinId AND subasta = @subJoyaId);

INSERT INTO Attendees (numeroPostor, cliente, subasta)
SELECT 501, @alejandroId, @subMuebId
WHERE NOT EXISTS (SELECT 1 FROM Attendees WHERE cliente = @alejandroId AND subasta = @subMuebId);

DECLARE @fotoDemo VARBINARY(MAX) = 0x89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C4890000000D49444154789C6360F8FFFF3F0005FE02FEA7F4819F0000000049454E44AE426082;

INSERT INTO Photos (producto, foto, orden)
SELECT p.identificador, @fotoDemo, n.orden
FROM Products p
CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6)) n(orden)
WHERE p.descripcionCatalogo LIKE 'Demo E3 -%'
  AND NOT EXISTS (
      SELECT 1
      FROM Photos ph
      WHERE ph.producto = p.identificador
        AND ph.orden = n.orden
  );

SELECT 'ENTREGA3_DEMO_SUBASTAS' AS tipo,
       a.identificador,
       a.fecha,
       a.hora,
       a.estado,
       a.categoria,
       a.moneda,
       a.ubicacion
FROM Auctions a
WHERE a.ubicacion IN ('Demo Entrega 3 - Sala Palermo', 'Demo Entrega 3 - Salon VIP', 'Demo Entrega 3 - Rosario')
ORDER BY a.identificador;

SELECT 'ITEMS_DEMO' AS tipo,
       a.ubicacion,
       ci.identificador AS itemId,
       p.identificador AS productoId,
       p.descripcionCatalogo,
       ci.precioBase,
       ci.comision,
       ci.vendido
FROM CatalogItems ci
INNER JOIN Products p ON ci.producto = p.identificador
INNER JOIN Catalogs c ON ci.catalogo = c.identificador
INNER JOIN Auctions a ON c.subasta = a.identificador
WHERE p.descripcionCatalogo LIKE 'Demo E3 -%'
ORDER BY a.identificador, ci.identificador;
GO
