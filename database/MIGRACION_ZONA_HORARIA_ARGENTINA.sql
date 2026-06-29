/* Auct.io - normalizacion de fechas operativas a America/Argentina/Buenos_Aires (UTC-3).
   SQL Server almacena DATETIME sin zona. La aplicacion lo interpreta como hora local argentina. */
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Cambios TABLE (tabla sysname, columna sysname);
INSERT INTO @Cambios (tabla, columna) VALUES
('Attendees','fechaIngreso'),
('AuctionRecords','fechaVenta'),
('Auctions','fechaAlta'),
('Bids','fechaHora'),
('Fines','fechaGeneracion'),
('Insurance','fechaAlta'),
('Insurances','fechaAlta'),
('Notifications','fechaHora'),
('PaymentMethods','fechaAlta'),
('Products','fechaAlta'),
('Users','fechaAlta');

DECLARE @tabla sysname, @columna sysname, @constraint sysname, @sql nvarchar(max);
DECLARE defaults_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT tabla, columna FROM @Cambios;
OPEN defaults_cursor;
FETCH NEXT FROM defaults_cursor INTO @tabla, @columna;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID(N'dbo.' + QUOTENAME(@tabla), 'U') IS NOT NULL
    BEGIN
        SELECT @constraint = dc.name
        FROM sys.default_constraints dc
        INNER JOIN sys.columns c ON c.default_object_id=dc.object_id
        INNER JOIN sys.tables t ON t.object_id=c.object_id
        WHERE t.name=@tabla AND c.name=@columna;

        IF @constraint IS NOT NULL
        BEGIN
            SET @sql=N'ALTER TABLE dbo.' + QUOTENAME(@tabla) + N' DROP CONSTRAINT ' + QUOTENAME(@constraint) + N';';
            EXEC sp_executesql @sql;
        END;

        SET @sql=N'ALTER TABLE dbo.' + QUOTENAME(@tabla) + N' ADD CONSTRAINT '
            + QUOTENAME('DF_AR_' + @tabla + '_' + @columna)
            + N' DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())) FOR ' + QUOTENAME(@columna) + N';';
        EXEC sp_executesql @sql;
    END;

    SET @constraint=NULL;
    FETCH NEXT FROM defaults_cursor INTO @tabla, @columna;
END;
CLOSE defaults_cursor;
DEALLOCATE defaults_cursor;

SELECT DATEADD(HOUR,-3,SYSUTCDATETIME()) AS horaArgentina,
       SYSUTCDATETIME() AS horaUtc,
       DATEDIFF(HOUR, SYSUTCDATETIME(), DATEADD(HOUR,-3,SYSUTCDATETIME())) AS diferenciaHoras;
