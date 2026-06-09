

use auction;
GO

DECLARE @empleadoId   INT;
DECLARE @subastadorId INT;
DECLARE @alejandroId  INT;
DECLARE @martinId     INT;
DECLARE @sofiaId      INT;

SELECT @empleadoId   = identificador FROM Users WHERE documento = '20000111';
SELECT @subastadorId = identificador FROM Users WHERE documento = '20000222';
SELECT @alejandroId  = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId     = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId      = identificador FROM Users WHERE documento = '31888777';

/* ============================================================
   SUBASTAS
   ============================================================ */

INSERT INTO Auctions (fecha, hora, estado, subastador, ubicacion, capacidadAsistentes, tieneDeposito, seguridadPropia, categoria, moneda)
VALUES
-- Subasta activa hoy (en_curso) — Arte & Antigüedades — pesos — común
(CAST(GETDATE() AS DATE), '21:02', 'en_curso',  @subastadorId, 'Buenos Aires - Sala Palermo', 120, 'si', 'si', 'comun',   'pesos'),
-- Subasta abierta prox. semana — Joyería & Relojes — dólares — oro
(DATEADD(DAY, 7,  CAST(GETDATE() AS DATE)), '21:10', 'abierta', @subastadorId, 'Buenos Aires - Salón VIP',    60,  'si', 'si', 'oro',     'dolares'),
-- Subasta programada a futuro — Muebles & Diseño — pesos — plata
(DATEADD(DAY, 21, CAST(GETDATE() AS DATE)), '21:10', 'programada', @subastadorId, 'Rosario - Centro Cultural', 90,  'si', 'no', 'plata',   'pesos');
GO

DECLARE @empleadoId   INT;
DECLARE @subastadorId INT;
DECLARE @alejandroId  INT;
DECLARE @martinId     INT;
DECLARE @sofiaId      INT;

SELECT @empleadoId   = identificador FROM Users WHERE documento = '20000111';
SELECT @subastadorId = identificador FROM Users WHERE documento = '20000222';
SELECT @alejandroId  = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId     = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId      = identificador FROM Users WHERE documento = '31888777';

DECLARE @subArteId   INT;
DECLARE @subJoyaId   INT;
DECLARE @subMuebId   INT;

SELECT @subArteId = identificador FROM Auctions WHERE ubicacion = 'Buenos Aires - Sala Palermo';
SELECT @subJoyaId = identificador FROM Auctions WHERE ubicacion = 'Buenos Aires - Salón VIP';
SELECT @subMuebId = identificador FROM Auctions WHERE ubicacion = 'Rosario - Centro Cultural';

/* ============================================================
   SEGUROS adicionales (requeridos por los nuevos productos)
   ============================================================ */

INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
VALUES
('POL-ALE-002', @alejandroId, 'Zurich Argentina',  'si', 500000.00),
('POL-MAR-002', @martinId,    'BBVA Seguros',       'no', 320000.00),
('POL-SOF-001', @sofiaId,     'Federación Patronal','si', 180000.00);

/* ============================================================
   PRODUCTOS — Arte & Antigüedades (dueño: Alejandro)
   ============================================================ */

INSERT INTO Products (fecha, disponible, descripcionCatalogo, descripcionCompleta, historia, artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad, origenLicito, estadoAprobacion, revisor, duenio, seguro)
VALUES
(CAST(GETDATE() AS DATE), 'si',
 'Escultura en bronce — "El Pensador" réplica firmada',
 'Escultura en bronce fundido, réplica numerada y firmada de edición limitada (N°14/50). Altura 38 cm. Base de mármol negro incluida.',
 'Adquirida en galería Ateneo de Buenos Aires en 2003. Certificado de autenticidad emitido por el artista. Nunca exhibida en forma pública.',
 'Rodolfo Haber', '2003-09-15', 'Depósito A - Estante 7',
 'si', 'si', 'aceptado', @empleadoId, @alejandroId, 'POL-ALE-002'),

(CAST(GETDATE() AS DATE), 'si',
 'Óleo sobre tela — Paisaje pampeano circa 1940',
 'Óleo sobre tela de gran formato (120×90 cm). Firmado en ángulo inferior derecho. Marco original de madera tallada. Excelente estado de conservación.',
 'Pieza heredada de colección familiar. Atribuida a pintor de la Escuela Rioplatense. Valoración pericial de $85.000 realizada en 2022.',
 'Anónimo (Escuela Rioplatense)', '1940-01-01', 'Depósito B - Sala Pintura 2',
 'si', 'si', 'aceptado', @empleadoId, @alejandroId, 'POL-ALE-002'),

(CAST(GETDATE() AS DATE), 'si',
 'Juego de porcelana alemana — Meissen circa 1890',
 'Juego de té completo de porcelana Meissen (12 piezas). Decoración floral pintada a mano. Sin piezas faltantes ni restauraciones. Estuche original de caoba.',
 'Importado a Argentina a principios del siglo XX por familia de inmigrantes europeos. Documentación de importación y valoración incluidas.',
 NULL, '1890-01-01', 'Depósito C - Vitrina Colección',
 'si', 'si', 'aceptado', @empleadoId, @alejandroId, 'POL-ALE-001'),

(CAST(GETDATE() AS DATE), 'si',
 'Mapa cartográfico original — Buenos Aires 1867',
 'Mapa litográfico original de la Ciudad de Buenos Aires, año 1867. Dimensiones 55×40 cm. Encuadrado con vidrio UV. Incluye certificado de autenticidad del Archivo General de la Nación.',
 'Adquirido en subasta de documentos históricos realizada en 2015. Pieza de alto valor documental e histórico.',
 'Ignacio Colquhoun (cartógrafo)', '1867-01-01', 'Depósito C - Sector Documentos',
 'si', 'si', 'aceptado', @empleadoId, @alejandroId, 'POL-ALE-002'),

(CAST(GETDATE() AS DATE), 'si',
 'Biblioteca estilo inglés — caoba maciza c.1920',
 'Biblioteca de dos cuerpos en caoba maciza. 6 estantes con vidrio biselado. Herrajes originales en bronce. 220×180×45 cm. Restaurada profesionalmente en 2019.',
 'Procedente de casona de San Isidro. Historia documentada de la propiedad. Estado excelente post-restauración.',
 NULL, '1920-01-01', 'Depósito D - Muebles Grandes',
 'si', 'si', 'aceptado', @empleadoId, @alejandroId, 'POL-ALE-002');

/* ============================================================
   PRODUCTOS — Joyería & Relojes (dueño: Martín)
   ============================================================ */

INSERT INTO Products (fecha, disponible, descripcionCatalogo, descripcionCompleta, historia, artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad, origenLicito, estadoAprobacion, revisor, duenio, seguro)
VALUES
(CAST(GETDATE() AS DATE), 'si',
 'Collar de perlas naturales — 3 hileras',
 'Collar de perlas naturales cultivadas en Japón. 3 hileras, cierre en oro blanco 18k con brillantes. Largo 45 cm. Estuche original Mikimoto. Certificado GIA incluido.',
 'Adquirido en joyería Mikimoto Tokio en 1998. Perteneció a reconocida familia bonaerense. Nunca modificado ni restaurado.',
 'Mikimoto', '1998-03-20', 'Caja Fuerte B - Joyería',
 'si', 'si', 'aceptado', @empleadoId, @martinId, 'POL-MAR-002'),

(CAST(GETDATE() AS DATE), 'si',
 'Reloj Rolex Datejust — Acero y oro — Ref. 16233',
 'Rolex Datejust referencia 16233, caja 36mm en acero y oro amarillo 18k. Dial plateado con índices dorados. Movimiento automático calibre 3135. Con caja, papeles y garantía original año 2001.',
 'Reloj de caballero adquirido nuevo en joyería Riviera, Buenos Aires, 2001. Uso esporádico. Service oficial realizado en 2018. Estado impecable.',
 'Rolex SA', '2001-06-10', 'Caja Fuerte B - Relojes',
 'si', 'si', 'aceptado', @empleadoId, @martinId, 'POL-MAR-002'),

(CAST(GETDATE() AS DATE), 'si',
 'Anillo art déco — platino y diamantes',
 'Anillo de platino con diamante central talla cojín de 1.8 quilates (H/VS2, certificado GIA) rodeado de 12 diamantes talla baguette. Diseño art déco circa 1935.',
 'Pieza de joyería de herencia familiar. Valoración gemológica realizada en 2023 por Instituto Gemológico Argentino. Certificado de autenticidad adjunto.',
 NULL, '1935-01-01', 'Caja Fuerte B - Joyería',
 'si', 'si', 'aceptado', @empleadoId, @martinId, 'POL-MAR-001'),

(CAST(GETDATE() AS DATE), 'si',
 'Monedas de oro — colección 10 piezas argentinas s.XIX',
 'Colección de 10 monedas de oro argentinas del siglo XIX. Incluye: 2 onzas de 1826, 4 patacones de 1836-1848, y 4 libras esterlinas acuñadas en Buenos Aires. Estado: VF a AU. Estuche de exhibición incluido.',
 'Colección formada a lo largo de 30 años por numismático aficionado. Valuación de la Sociedad Numismática Argentina realizada en 2021.',
 NULL, '1826-01-01', 'Caja Fuerte A - Numismática',
 'si', 'si', 'aceptado', @empleadoId, @martinId, 'POL-MAR-002'),

(CAST(GETDATE() AS DATE), 'si',
 'Pulsera tennis — oro blanco 18k y brillantes',
 'Pulsera tennis en oro blanco 18k con 42 brillantes de 0.12 ct cada uno (total 5.04 ct). Cierre de seguridad doble. Largo 18 cm. Peso 12.4 g. Certificado del taller de joyería incluido.',
 'Encargada a medida en taller de joyería artesanal en Montevideo, 2010. Nunca usada. Guardada en estuche original.',
 'Joyería Haber & Cía.', '2010-11-05', 'Caja Fuerte B - Joyería',
 'si', 'si', 'aceptado', @empleadoId, @martinId, 'POL-MAR-002');

/* ============================================================
   PRODUCTOS — Muebles & Diseño (dueño: Sofía)
   ============================================================ */

INSERT INTO Products (fecha, disponible, descripcionCatalogo, descripcionCompleta, historia, artistaDiseniador, fechaObjeto, ubicacionDeposito, declaracionPropiedad, origenLicito, estadoAprobacion, revisor, duenio, seguro)
VALUES
(CAST(GETDATE() AS DATE), 'si',
 'Sillón Barcelona — Mies van der Rohe — original Knoll',
 'Sillón Barcelona original fabricado por Knoll International. Estructura en acero inoxidable. Tapizado en cuero negro plena flor. Etiqueta Knoll visible. Año de fabricación aproximado 1985.',
 'Adquirido en liquidación de oficina de arquitectura clausurada. Autenticidad confirmada por etiqueta y factura de compra original de Knoll Argentina.',
 'Ludwig Mies van der Rohe', '1985-01-01', 'Depósito D - Muebles Diseño',
 'si', 'si', 'aceptado', @empleadoId, @sofiaId, 'POL-SOF-001'),

(CAST(GETDATE() AS DATE), 'si',
 'Mesa ratona Noguchi — reproducción autorizada Herman Miller',
 'Mesa ratona Noguchi, reproducción autorizada por Herman Miller. Base de madera de nogal lacada y vidrio templado biselado de 19mm. Dimensiones: 128×91 cm. En excelente estado.',
 'Adquirida en showroom oficial Herman Miller Buenos Aires, 2008. Uso doméstico esporádico. Sin rayones ni imperfecciones.',
 'Isamu Noguchi', '2008-04-22', 'Depósito D - Muebles Diseño',
 'si', 'si', 'aceptado', @empleadoId, @sofiaId, 'POL-SOF-001'),

(CAST(GETDATE() AS DATE), 'si',
 'Lámpara Arco — Achille Castiglioni — Flos original',
 'Lámpara Arco original de Flos, Italia. Base de mármol blanco de Carrara (35 kg), tubo de acero cromado y pantalla de aluminio pulido. Altura máxima 250 cm. Con certificado de autenticidad Flos.',
 'Importada directamente de Italia en 1994. Funcionamiento perfecto. Certificado Flos y factura original de importación adjuntos.',
 'Achille Castiglioni', '1994-01-01', 'Depósito E - Iluminación',
 'si', 'si', 'aceptado', @empleadoId, @sofiaId, 'POL-SOF-001'),

(CAST(GETDATE() AS DATE), 'si',
 'Juego de living estilo Luis XVI — tapizado seda',
 'Juego de living compuesto por sofá de 3 cuerpos + 2 sillones individuales, estilo Luis XVI. Estructura en madera de haya dorada. Tapizado en seda color marfil bordada. Circa 1970. Restaurado 2020.',
 'Proveniente de propiedad en Recoleta. Restauración completa de tapizado y dorado realizada por tapicero especializado en muebles franceses.',
 NULL, '1970-01-01', 'Depósito D - Muebles Grandes',
 'si', 'si', 'aceptado', @empleadoId, @sofiaId, 'POL-SOF-001'),

(CAST(GETDATE() AS DATE), 'si',
 'Vitrina exhibidora Empire — caoba y bronce dorado',
 'Vitrina estilo Empire en caoba con apliques de bronce dorado. 4 puertas vidriadas, interior tapizado en terciopelo verde. 190×140×45 cm. Circa 1850. Procedencia francesa documentada.',
 'Importada de Francia a Argentina hacia 1920. Documentación de aduana y valoración de antiqüerio disponibles. Estado estructural perfecto, vidrios originales.',
 NULL, '1850-01-01', 'Depósito C - Vitrina Colección',
 'si', 'si', 'aceptado', @empleadoId, @sofiaId, 'POL-SOF-001');
GO

/* ============================================================
   CATÁLOGOS + CATALOG ITEMS
   ============================================================ */

DECLARE @empleadoId INT;
SELECT @empleadoId = identificador FROM Users WHERE documento = '20000111';

DECLARE @subArteId INT;
DECLARE @subJoyaId INT;
DECLARE @subMuebId INT;

SELECT @subArteId = identificador FROM Auctions WHERE ubicacion = 'Buenos Aires - Sala Palermo';
SELECT @subJoyaId = identificador FROM Auctions WHERE ubicacion = 'Buenos Aires - Salón VIP';
SELECT @subMuebId = identificador FROM Auctions WHERE ubicacion = 'Rosario - Centro Cultural';

/* Catálogos */
INSERT INTO Catalogs (descripcion, subasta, responsable)
VALUES
('Arte & Antigüedades — Subasta junio 2026',   @subArteId, @empleadoId),
('Joyería & Relojes — Subasta julio 2026',      @subJoyaId, @empleadoId),
('Muebles & Diseño — Subasta julio 2026',       @subMuebId, @empleadoId);

DECLARE @catArteId INT;
DECLARE @catJoyaId INT;
DECLARE @catMuebId INT;

SELECT @catArteId = identificador FROM Catalogs WHERE subasta = @subArteId;
SELECT @catJoyaId = identificador FROM Catalogs WHERE subasta = @subJoyaId;
SELECT @catMuebId = identificador FROM Catalogs WHERE subasta = @subMuebId;

/* IDs de productos */
DECLARE @pEscultura   INT; SELECT @pEscultura   = identificador FROM Products WHERE descripcionCatalogo = 'Escultura en bronce — "El Pensador" réplica firmada';
DECLARE @pOleo        INT; SELECT @pOleo        = identificador FROM Products WHERE descripcionCatalogo = 'Óleo sobre tela — Paisaje pampeano circa 1940';
DECLARE @pPorcelana   INT; SELECT @pPorcelana   = identificador FROM Products WHERE descripcionCatalogo = 'Juego de porcelana alemana — Meissen circa 1890';
DECLARE @pMapa        INT; SELECT @pMapa        = identificador FROM Products WHERE descripcionCatalogo = 'Mapa cartográfico original — Buenos Aires 1867';
DECLARE @pBiblioteca  INT; SELECT @pBiblioteca  = identificador FROM Products WHERE descripcionCatalogo = 'Biblioteca estilo inglés — caoba maciza c.1920';

DECLARE @pCollar      INT; SELECT @pCollar      = identificador FROM Products WHERE descripcionCatalogo = 'Collar de perlas naturales — 3 hileras';
DECLARE @pRolex       INT; SELECT @pRolex       = identificador FROM Products WHERE descripcionCatalogo = 'Reloj Rolex Datejust — Acero y oro — Ref. 16233';
DECLARE @pAnillo      INT; SELECT @pAnillo      = identificador FROM Products WHERE descripcionCatalogo = 'Anillo art déco — platino y diamantes';
DECLARE @pMonedas     INT; SELECT @pMonedas     = identificador FROM Products WHERE descripcionCatalogo = 'Monedas de oro — colección 10 piezas argentinas s.XIX';
DECLARE @pPulsera     INT; SELECT @pPulsera     = identificador FROM Products WHERE descripcionCatalogo = 'Pulsera tennis — oro blanco 18k y brillantes';

DECLARE @pSillon      INT; SELECT @pSillon      = identificador FROM Products WHERE descripcionCatalogo = 'Sillón Barcelona — Mies van der Rohe — original Knoll';
DECLARE @pNoguchi     INT; SELECT @pNoguchi     = identificador FROM Products WHERE descripcionCatalogo = 'Mesa ratona Noguchi — reproducción autorizada Herman Miller';
DECLARE @pArco        INT; SELECT @pArco        = identificador FROM Products WHERE descripcionCatalogo = 'Lámpara Arco — Achille Castiglioni — Flos original';
DECLARE @pLouis       INT; SELECT @pLouis       = identificador FROM Products WHERE descripcionCatalogo = 'Juego de living estilo Luis XVI — tapizado seda';
DECLARE @pVitrina     INT; SELECT @pVitrina     = identificador FROM Products WHERE descripcionCatalogo = 'Vitrina exhibidora Empire — caoba y bronce dorado';

/* CatalogItems — Arte & Antigüedades (pesos) */
INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
VALUES
(@catArteId, @pEscultura,  45000.00,  4500.00, 'no', 'no'),
(@catArteId, @pOleo,       85000.00,  8500.00, 'no', 'no'),
(@catArteId, @pPorcelana,  60000.00,  6000.00, 'no', 'no'),
(@catArteId, @pMapa,       30000.00,  3000.00, 'no', 'no'),
(@catArteId, @pBiblioteca, 120000.00, 12000.00,'no', 'no');

/* CatalogItems — Joyería & Relojes (dólares) */
INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
VALUES
(@catJoyaId, @pCollar,  8500.00,  850.00, 'no', 'no'),
(@catJoyaId, @pRolex,  12000.00, 1200.00, 'no', 'no'),
(@catJoyaId, @pAnillo,  9500.00,  950.00, 'no', 'no'),
(@catJoyaId, @pMonedas, 6000.00,  600.00, 'no', 'no'),
(@catJoyaId, @pPulsera, 7200.00,  720.00, 'no', 'no');

/* CatalogItems — Muebles & Diseño (pesos) */
INSERT INTO CatalogItems (catalogo, producto, precioBase, comision, subastado, vendido)
VALUES
(@catMuebId, @pSillon,   95000.00,  9500.00, 'no', 'no'),
(@catMuebId, @pNoguchi,  75000.00,  7500.00, 'no', 'no'),
(@catMuebId, @pArco,     55000.00,  5500.00, 'no', 'no'),
(@catMuebId, @pLouis,   140000.00, 14000.00, 'no', 'no'),
(@catMuebId, @pVitrina, 180000.00, 18000.00, 'no', 'no');
GO

/* ============================================================
   ASISTENTES — inscribir a los 3 clientes en las subastas nuevas
   ============================================================ */

DECLARE @alejandroId INT;
DECLARE @martinId    INT;
DECLARE @sofiaId     INT;

SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId    = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId     = identificador FROM Users WHERE documento = '31888777';

DECLARE @subArteId INT;
DECLARE @subJoyaId INT;
DECLARE @subMuebId INT;

SELECT @subArteId = identificador FROM Auctions WHERE ubicacion = 'Buenos Aires - Sala Palermo';
SELECT @subJoyaId = identificador FROM Auctions WHERE ubicacion = 'Buenos Aires - Salón VIP';
SELECT @subMuebId = identificador FROM Auctions WHERE ubicacion = 'Rosario - Centro Cultural';

INSERT INTO Attendees (numeroPostor, cliente, subasta)
VALUES
(301, @alejandroId, @subArteId),
(302, @martinId,    @subArteId),
(303, @sofiaId,     @subArteId),
(401, @alejandroId, @subJoyaId),
(402, @martinId,    @subJoyaId),
(501, @alejandroId, @subMuebId),
(502, @sofiaId,     @subMuebId);
GO

/* ============================================================
   VERIFICACIÓN
   ============================================================ */

SELECT 'NUEVAS_SUBASTAS' AS tipo,
       a.identificador, a.fecha, a.hora, a.estado, a.categoria, a.moneda, a.ubicacion
FROM Auctions a
WHERE a.ubicacion IN ('Buenos Aires - Sala Palermo','Buenos Aires - Salón VIP','Rosario - Centro Cultural')
ORDER BY a.identificador;

SELECT 'ITEMS_ARTE' AS tipo, ci.identificador AS itemId, p.descripcionCatalogo, ci.precioBase, ci.comision
FROM CatalogItems ci
INNER JOIN Products p ON ci.producto = p.identificador
INNER JOIN Catalogs c ON ci.catalogo = c.identificador
INNER JOIN Auctions a ON c.subasta = a.identificador
WHERE a.ubicacion = 'Buenos Aires - Sala Palermo';

SELECT 'ITEMS_JOYA' AS tipo, ci.identificador AS itemId, p.descripcionCatalogo, ci.precioBase, ci.comision
FROM CatalogItems ci
INNER JOIN Products p ON ci.producto = p.identificador
INNER JOIN Catalogs c ON ci.catalogo = c.identificador
INNER JOIN Auctions a ON c.subasta = a.identificador
WHERE a.ubicacion = 'Buenos Aires - Salón VIP';

SELECT 'ITEMS_MUEBLES' AS tipo, ci.identificador AS itemId, p.descripcionCatalogo, ci.precioBase, ci.comision
FROM CatalogItems ci
INNER JOIN Products p ON ci.producto = p.identificador
INNER JOIN Catalogs c ON ci.catalogo = c.identificador
INNER JOIN Auctions a ON c.subasta = a.identificador
WHERE a.ubicacion = 'Rosario - Centro Cultural';
GO