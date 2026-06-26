USE auction;
GO

/* Datos base para demo Entrega 3.
   Ejecutar despues de CREAR_BASE.sql y antes de ENTREGA3_DEMO_SUBASTAS.sql. */

IF NOT EXISTS (SELECT 1 FROM Countries WHERE numero = 32)
BEGIN
    INSERT INTO Countries (numero, nombre, nombreCorto, capital, nacionalidad, idiomas)
    VALUES (32, 'Argentina', 'AR', 'Buenos Aires', 'Argentina', 'Español');
END
GO

IF NOT EXISTS (SELECT 1 FROM Countries WHERE numero = 840)
BEGIN
    INSERT INTO Countries (numero, nombre, nombreCorto, capital, nacionalidad, idiomas)
    VALUES (840, 'Estados Unidos', 'US', 'Washington DC', 'Estadounidense', 'Ingles');
END
GO

INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT '20000111', 'Admin', 'Demo', 'admin@auctio.test', '1100001111', 'Sede central', 'activo', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '20000111');

INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT '20000222', 'Martillero', 'Principal', 'martillero@auctio.test', '1100002222', 'Sede central', 'activo', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '20000222');

INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT '30123456', 'Alejandro', 'Demo', 'alejandro@auctio.test', '1130123456', 'Av. Demo 123', 'activo', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '30123456');

INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT '30999888', 'Martin', 'Demo', 'martin@auctio.test', '1130999888', 'Calle Demo 456', 'activo', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '30999888');

INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT '31888777', 'Sofia', 'Demo', 'sofia@auctio.test', '1131888777', 'Pasaje Demo 789', 'activo', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '31888777');

INSERT INTO Users (documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT '40000111', 'Pendiente', 'Demo', 'pendiente@auctio.test', '1140000111', 'Pendiente 111', 'pendiente', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '40000111');

SET IDENTITY_INSERT Users ON;

INSERT INTO Users (identificador, documento, nombre, apellido, email, telefono, direccion, estado, clave)
SELECT 9000007, '90000007', 'Auctio', 'Empresa', 'empresa@auctio.test', '1190000007', 'Sede central', 'activo', '1234'
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE documento = '90000007')
  AND NOT EXISTS (SELECT 1 FROM Users WHERE identificador = 9000007);

SET IDENTITY_INSERT Users OFF;
GO

SET IDENTITY_INSERT Sectors ON;

INSERT INTO Sectors (identificador, nombreSector, codigoSector, responsableSector)
SELECT 1, 'Operaciones', 'OPS', NULL
WHERE NOT EXISTS (SELECT 1 FROM Sectors WHERE identificador = 1);

SET IDENTITY_INSERT Sectors OFF;
GO

DECLARE @adminId INT;
SELECT @adminId = identificador FROM Users WHERE documento = '20000111';

INSERT INTO Employees (identificador, cargo, sector)
SELECT @adminId, 'Verificador interno', 1
WHERE @adminId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Employees WHERE identificador = @adminId);
GO

DECLARE @adminId INT;
SELECT @adminId = identificador FROM Users WHERE documento = '20000111';

UPDATE Sectors
SET responsableSector = @adminId
WHERE identificador = 1
  AND @adminId IS NOT NULL
  AND responsableSector IS NULL;
GO

DECLARE @subastadorId INT;
SELECT @subastadorId = identificador FROM Users WHERE documento = '20000222';

INSERT INTO Auctioneers (identificador, matricula, region)
SELECT @subastadorId, 'MAT-2026-01', 'Buenos Aires'
WHERE @subastadorId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Auctioneers WHERE identificador = @subastadorId);
GO

DECLARE @adminId INT;
DECLARE @alejandroId INT;
DECLARE @martinId INT;
DECLARE @sofiaId INT;
DECLARE @pendienteId INT;
DECLARE @empresaId INT;

SELECT @adminId = identificador FROM Users WHERE documento = '20000111';
SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId = identificador FROM Users WHERE documento = '31888777';
SELECT @pendienteId = identificador FROM Users WHERE documento = '40000111';
SELECT @empresaId = identificador FROM Users WHERE documento = '90000007';

INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
SELECT @alejandroId, 32, 'si', 'oro', @adminId
WHERE @alejandroId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Clients WHERE identificador = @alejandroId);

INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
SELECT @martinId, 32, 'si', 'platino', @adminId
WHERE @martinId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Clients WHERE identificador = @martinId);

INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
SELECT @sofiaId, 32, 'si', 'plata', @adminId
WHERE @sofiaId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Clients WHERE identificador = @sofiaId);

INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
SELECT @pendienteId, 32, 'no', 'comun', NULL
WHERE @pendienteId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Clients WHERE identificador = @pendienteId);

INSERT INTO Clients (identificador, numeroPais, admitido, categoria, verificador)
SELECT @empresaId, 32, 'si', 'platino', @adminId
WHERE @empresaId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Clients WHERE identificador = @empresaId);
GO

DECLARE @adminId INT;
DECLARE @alejandroId INT;
DECLARE @martinId INT;
DECLARE @sofiaId INT;
DECLARE @empresaId INT;

SELECT @adminId = identificador FROM Users WHERE documento = '20000111';
SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId = identificador FROM Users WHERE documento = '31888777';
SELECT @empresaId = identificador FROM Users WHERE documento = '90000007';

INSERT INTO Owners (identificador, numeroPais, verificacionFinanciera, verificacionJudicial, calificacionRiesgo, verificador)
SELECT @alejandroId, 32, 'si', 'si', 2, @adminId
WHERE @alejandroId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Owners WHERE identificador = @alejandroId);

INSERT INTO Owners (identificador, numeroPais, verificacionFinanciera, verificacionJudicial, calificacionRiesgo, verificador)
SELECT @martinId, 32, 'si', 'si', 1, @adminId
WHERE @martinId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Owners WHERE identificador = @martinId);

INSERT INTO Owners (identificador, numeroPais, verificacionFinanciera, verificacionJudicial, calificacionRiesgo, verificador)
SELECT @sofiaId, 32, 'si', 'si', 3, @adminId
WHERE @sofiaId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Owners WHERE identificador = @sofiaId);

INSERT INTO Owners (identificador, numeroPais, verificacionFinanciera, verificacionJudicial, calificacionRiesgo, verificador)
SELECT @empresaId, 32, 'si', 'si', 1, @adminId
WHERE @empresaId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Owners WHERE identificador = @empresaId);
GO

DECLARE @alejandroId INT;
DECLARE @martinId INT;
DECLARE @sofiaId INT;

SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId = identificador FROM Users WHERE documento = '30999888';
SELECT @sofiaId = identificador FROM Users WHERE documento = '31888777';

INSERT INTO PaymentMethods (cliente, tipo, entidad, numeroReferencia, esExtranjera, moneda, verificado, montoCheque, montoDisponible)
SELECT @alejandroId, 'tarjeta_credito', 'Visa Demo', '4111-XXXX-XXXX-3012', 'no', 'pesos', 'si', NULL, NULL
WHERE @alejandroId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM PaymentMethods WHERE cliente = @alejandroId AND numeroReferencia = '4111-XXXX-XXXX-3012');

INSERT INTO PaymentMethods (cliente, tipo, entidad, numeroReferencia, esExtranjera, moneda, verificado, montoCheque, montoDisponible)
SELECT @alejandroId, 'tarjeta_credito', 'Visa Internacional Demo', '4222-XXXX-XXXX-3012', 'si', 'dolares', 'si', NULL, NULL
WHERE @alejandroId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM PaymentMethods WHERE cliente = @alejandroId AND numeroReferencia = '4222-XXXX-XXXX-3012');

INSERT INTO PaymentMethods (cliente, tipo, entidad, numeroReferencia, esExtranjera, moneda, verificado, montoCheque, montoDisponible)
SELECT @martinId, 'cuenta_bancaria', 'Banco Demo', 'CBU-30999888', 'no', 'pesos', 'si', NULL, NULL
WHERE @martinId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM PaymentMethods WHERE cliente = @martinId AND numeroReferencia = 'CBU-30999888');

INSERT INTO PaymentMethods (cliente, tipo, entidad, numeroReferencia, esExtranjera, moneda, verificado, montoCheque, montoDisponible)
SELECT @sofiaId, 'cheque_certificado', 'Banco Demo', 'CHQ-31888777', 'no', 'pesos', 'si', 500000.00, 500000.00
WHERE @sofiaId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM PaymentMethods WHERE cliente = @sofiaId AND numeroReferencia = 'CHQ-31888777');
GO

DECLARE @alejandroId INT;
DECLARE @martinId INT;

SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId = identificador FROM Users WHERE documento = '30999888';

INSERT INTO OwnerBankAccounts (duenio, banco, numeroCuenta, esExterior, moneda)
SELECT @alejandroId, 'Banco Demo', 'CTA-ALE-001', 'no', 'pesos'
WHERE @alejandroId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM OwnerBankAccounts WHERE duenio = @alejandroId AND numeroCuenta = 'CTA-ALE-001');

INSERT INTO OwnerBankAccounts (duenio, banco, numeroCuenta, esExterior, moneda)
SELECT @martinId, 'Banco Exterior Demo', 'CTA-MAR-USD-001', 'si', 'dolares'
WHERE @martinId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM OwnerBankAccounts WHERE duenio = @martinId AND numeroCuenta = 'CTA-MAR-USD-001');
GO

DECLARE @alejandroId INT;
DECLARE @martinId INT;

SELECT @alejandroId = identificador FROM Users WHERE documento = '30123456';
SELECT @martinId = identificador FROM Users WHERE documento = '30999888';

INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
SELECT 'POL-ALE-001', @alejandroId, 'Zurich Argentina', 'no', 250000.00
WHERE @alejandroId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Insurances WHERE nroPoliza = 'POL-ALE-001');

INSERT INTO Insurances (nroPoliza, duenio, compania, polizaCombinada, importe)
SELECT 'POL-MAR-001', @martinId, 'BBVA Seguros', 'no', 210000.00
WHERE @martinId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM Insurances WHERE nroPoliza = 'POL-MAR-001');
GO
