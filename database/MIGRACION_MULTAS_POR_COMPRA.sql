IF COL_LENGTH('dbo.Fines', 'venta') IS NULL
BEGIN
    ALTER TABLE dbo.Fines ADD venta INT NULL;
END
GO

IF OBJECT_ID('dbo.fk_fines_auctionRecords', 'F') IS NULL
BEGIN
    ALTER TABLE dbo.Fines WITH CHECK
    ADD CONSTRAINT fk_fines_auctionRecords FOREIGN KEY (venta)
    REFERENCES dbo.AuctionRecords (identificador);
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'ux_fines_venta' AND object_id = OBJECT_ID('dbo.Fines')
)
BEGIN
    CREATE UNIQUE INDEX ux_fines_venta ON dbo.Fines(venta) WHERE venta IS NOT NULL;
END
GO
