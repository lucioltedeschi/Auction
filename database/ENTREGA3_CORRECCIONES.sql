USE auction;
GO

IF COL_LENGTH('Products', 'precioBaseSugerido') IS NULL
BEGIN
    ALTER TABLE Products ADD precioBaseSugerido DECIMAL(18, 2) NULL;
END
GO

IF COL_LENGTH('Products', 'precioBasePropuesto') IS NULL
BEGIN
    ALTER TABLE Products ADD precioBasePropuesto DECIMAL(18, 2) NULL;
END
GO

IF COL_LENGTH('Products', 'comisionPropuesta') IS NULL
BEGIN
    ALTER TABLE Products ADD comisionPropuesta DECIMAL(18, 2) NULL;
END
GO

IF COL_LENGTH('Products', 'condicionesPropuestas') IS NULL
BEGIN
    ALTER TABLE Products ADD condicionesPropuestas VARCHAR(500) NULL;
END
GO

IF COL_LENGTH('Products', 'fechaPropuesta') IS NULL
BEGIN
    ALTER TABLE Products ADD fechaPropuesta DATETIME NULL;
END
GO

IF COL_LENGTH('Products', 'estadoAprobacion') < 30
BEGIN
    UPDATE Products
    SET estadoAprobacion = 'pendiente'
    WHERE estadoAprobacion IS NULL;

    ALTER TABLE Products ALTER COLUMN estadoAprobacion VARCHAR(30) NOT NULL;
END
GO

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_products_estadoAprobacion'
      AND parent_object_id = OBJECT_ID('dbo.Products')
)
BEGIN
    ALTER TABLE dbo.Products DROP CONSTRAINT chk_products_estadoAprobacion;
END
GO

ALTER TABLE dbo.Products WITH CHECK
ADD CONSTRAINT chk_products_estadoAprobacion
CHECK (
    estadoAprobacion IN (
        'pendiente',
        'aceptado',
        'rechazado',
        'pendiente_inspeccion',
        'propuesta_enviada',
        'aceptado_usuario',
        'rechazado_usuario',
        'incluido_subasta'
    )
);
GO

UPDATE Products
SET estadoAprobacion = 'pendiente_inspeccion'
WHERE estadoAprobacion = 'pendiente';
GO

-- Estados esperados desde entrega 3:
-- pendiente_inspeccion, rechazado, propuesta_enviada,
-- aceptado_usuario, rechazado_usuario, incluido_subasta.
