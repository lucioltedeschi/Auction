USE auction;
GO

/* Ejecutar despues de ENTREGA3_DEMO_SUBASTAS.sql si se quiere tener
   el usuario 31888777 bloqueado por multa pendiente. */

DECLARE @sofiaId INT;
DECLARE @subastaId INT;

SELECT @sofiaId = identificador FROM Users WHERE documento = '31888777';
SELECT TOP 1 @subastaId = identificador FROM Auctions ORDER BY identificador;

IF @sofiaId IS NOT NULL
   AND @subastaId IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM Fines
        WHERE cliente = @sofiaId
          AND subasta = @subastaId
          AND pagada = 'no'
   )
BEGIN
    INSERT INTO Fines (cliente, subasta, monto, pagada)
    VALUES (@sofiaId, @subastaId, 25000.00, 'no');
END
GO
