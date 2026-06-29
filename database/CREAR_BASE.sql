IF DB_ID('auction') IS NULL
BEGIN
    CREATE DATABASE auction;
END
GO

USE auction;
GO

IF OBJECT_ID('dbo.Attendees', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Attendees] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [numeroPostor] int NOT NULL,
        [cliente] int NOT NULL,
        [subasta] int NOT NULL,
        [fechaIngreso] datetime NOT NULL CONSTRAINT [DF__Attendees__fecha__236943A5] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        CONSTRAINT [pk_attendees] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Auctioneers', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Auctioneers] (
        [identificador] int NOT NULL,
        [matricula] varchar(15) NULL,
        [region] varchar(50) NULL,
        CONSTRAINT [pk_auctioneers] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.AuctionRecords', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[AuctionRecords] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [subasta] int NOT NULL,
        [duenio] int NOT NULL,
        [producto] int NOT NULL,
        [cliente] int NOT NULL,
        [medioPago] int NULL,
        [importe] decimal(18, 2) NOT NULL,
        [comision] decimal(18, 2) NOT NULL,
        [costoEnvio] decimal(18, 2) NULL,
        [estadoPago] varchar(20) NULL,
        [fechaVenta] datetime NOT NULL CONSTRAINT [DF__AuctionRe__fecha__3493CFA7] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        [retiroPersonal] varchar(2) NULL,
        CONSTRAINT [pk_auctionRecords] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Auctions', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Auctions] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [fecha] date NOT NULL,
        [hora] time(7) NOT NULL,
        [estado] varchar(20) NOT NULL,
        [subastador] int NULL,
        [ubicacion] varchar(350) NULL,
        [capacidadAsistentes] int NULL,
        [tieneDeposito] varchar(2) NULL,
        [seguridadPropia] varchar(2) NULL,
        [categoria] varchar(10) NOT NULL,
        [moneda] varchar(10) NOT NULL CONSTRAINT [DF__Auctions__moneda__02084FDA] DEFAULT ('pesos'),
        [duracionItemMinutos] int NOT NULL CONSTRAINT [DF__Auctions__duraci__02FC7413] DEFAULT ((180)),
        [fechaAlta] datetime NOT NULL CONSTRAINT [DF__Auctions__fechaA__03F0984C] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        CONSTRAINT [pk_auctions] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Bids', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Bids] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [asistente] int NOT NULL,
        [item] int NOT NULL,
        [importe] decimal(18, 2) NOT NULL,
        [fechaHora] datetime NOT NULL CONSTRAINT [DF__Bids__fechaHora__29221CFB] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        [ganador] varchar(2) NOT NULL CONSTRAINT [DF__Bids__ganador__2B0A656D] DEFAULT ('no'),
        [confirmado] varchar(2) NOT NULL CONSTRAINT [DF__Bids__confirmado__2CF2ADDF] DEFAULT ('si'),
        CONSTRAINT [pk_bids] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.CatalogItems', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[CatalogItems] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [catalogo] int NOT NULL,
        [producto] int NOT NULL,
        [precioBase] decimal(18, 2) NOT NULL,
        [comision] decimal(18, 2) NOT NULL,
        [subastado] varchar(2) NOT NULL,
        [vendido] varchar(2) NOT NULL CONSTRAINT [DF__CatalogIt__vendi__1DB06A4F] DEFAULT ('no'),
        CONSTRAINT [pk_catalogItems] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Catalogs', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Catalogs] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [descripcion] varchar(250) NOT NULL,
        [subasta] int NULL,
        [responsable] int NOT NULL,
        CONSTRAINT [pk_catalogs] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Clients', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Clients] (
        [identificador] int NOT NULL,
        [numeroPais] int NULL,
        [admitido] varchar(2) NOT NULL,
        [categoria] varchar(10) NOT NULL,
        [verificador] int NULL,
        CONSTRAINT [pk_clients] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Countries', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Countries] (
        [numero] int NOT NULL,
        [nombre] varchar(250) NOT NULL,
        [nombreCorto] varchar(250) NULL,
        [capital] varchar(250) NOT NULL,
        [nacionalidad] varchar(250) NOT NULL,
        [idiomas] varchar(150) NOT NULL,
        CONSTRAINT [pk_countries] PRIMARY KEY ([numero])
    );
END
GO

IF OBJECT_ID('dbo.Employees', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Employees] (
        [identificador] int NOT NULL,
        [cargo] varchar(100) NULL,
        [sector] int NULL,
        CONSTRAINT [pk_employees] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Fines', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Fines] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [cliente] int NOT NULL,
        [subasta] int NOT NULL,
        [venta] int NULL,
        [monto] decimal(18, 2) NOT NULL,
        [pagada] varchar(2) NOT NULL CONSTRAINT [DF__Fines__pagada__3F115E1A] DEFAULT ('no'),
        [fechaGeneracion] datetime NOT NULL CONSTRAINT [DF__Fines__fechaGene__40058253] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        CONSTRAINT [pk_fines] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.fk_fines_auctionRecords', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Fines]
    ADD CONSTRAINT [fk_fines_auctionRecords] FOREIGN KEY ([venta])
    REFERENCES [dbo].[AuctionRecords] ([identificador]);
END
GO

IF OBJECT_ID('dbo.Insurances', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Insurances] (
        [nroPoliza] varchar(30) NOT NULL,
        [duenio] int NOT NULL,
        [compania] varchar(150) NOT NULL,
        [polizaCombinada] varchar(2) NOT NULL,
        [importe] decimal(18, 2) NOT NULL,
        [fechaAlta] datetime NOT NULL CONSTRAINT [DF__Insurance__fecha__76969D2E] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        CONSTRAINT [pk_insurances] PRIMARY KEY ([nroPoliza])
    );
END
GO

IF OBJECT_ID('dbo.Notifications', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Notifications] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [cliente] int NOT NULL,
        [titulo] varchar(150) NOT NULL,
        [mensaje] varchar(1000) NOT NULL,
        [fechaHora] datetime NOT NULL CONSTRAINT [DF__Notificat__fecha__44CA3770] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        [leida] varchar(2) NOT NULL CONSTRAINT [DF__Notificat__leida__46B27FE2] DEFAULT ('no'),
        CONSTRAINT [pk_notifications] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.OwnerBankAccounts', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[OwnerBankAccounts] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [duenio] int NOT NULL,
        [banco] varchar(150) NOT NULL,
        [numeroCuenta] varchar(150) NOT NULL,
        [esExterior] varchar(2) NOT NULL,
        [moneda] varchar(10) NULL,
        CONSTRAINT [pk_ownerBankAccounts] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Owners', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Owners] (
        [identificador] int NOT NULL,
        [numeroPais] int NULL,
        [verificacionFinanciera] varchar(2) NOT NULL,
        [verificacionJudicial] varchar(2) NOT NULL,
        [calificacionRiesgo] int NOT NULL,
        [verificador] int NULL,
        CONSTRAINT [pk_owners] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.PaymentMethods', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[PaymentMethods] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [cliente] int NOT NULL,
        [tipo] varchar(50) NOT NULL,
        [entidad] varchar(150) NULL,
        [numeroReferencia] varchar(150) NULL,
        [esExtranjera] varchar(2) NOT NULL,
        [moneda] varchar(10) NULL,
        [verificado] varchar(2) NOT NULL CONSTRAINT [DF__PaymentMe__verif__6B24EA82] DEFAULT ('no'),
        [montoCheque] decimal(18, 2) NULL,
        [montoDisponible] decimal(18, 2) NULL,
        [fechaAlta] datetime NOT NULL CONSTRAINT [DF__PaymentMe__fecha__6C190EBB] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        CONSTRAINT [pk_paymentMethods] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Photos', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Photos] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [producto] int NOT NULL,
        [foto] varbinary(MAX) NOT NULL,
        [orden] int NULL,
        CONSTRAINT [pk_photos] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Products', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Products] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [fecha] date NULL,
        [disponible] varchar(2) NOT NULL,
        [descripcionCatalogo] varchar(500) NULL CONSTRAINT [DF__Products__descri__08B54D69] DEFAULT ('No Posee'),
        [descripcionCompleta] varchar(500) NOT NULL,
        [historia] varchar(MAX) NULL,
        [artistaDiseniador] varchar(150) NULL,
        [fechaObjeto] date NULL,
        [ubicacionDeposito] varchar(250) NULL,
        [declaracionPropiedad] varchar(2) NOT NULL,
        [origenLicito] varchar(2) NOT NULL,
        [estadoAprobacion] varchar(30) NOT NULL CONSTRAINT [DF__Products__estado__0C85DE4D] DEFAULT ('pendiente'),
        [motivoRechazo] varchar(500) NULL,
        [revisor] int NULL,
        [duenio] int NOT NULL,
        [seguro] varchar(30) NULL,
        [fechaAlta] datetime NOT NULL CONSTRAINT [DF__Products__fechaA__0D7A0286] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        [precioBasePropuesto] decimal(18, 2) NULL,
        [comisionPropuesta] decimal(18, 2) NULL,
        [condicionesPropuestas] varchar(500) NULL,
        [fechaPropuesta] datetime NULL,
        [precioBaseSugerido] decimal(18, 2) NULL,
        CONSTRAINT [pk_products] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Sectors', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Sectors] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [nombreSector] varchar(150) NOT NULL,
        [codigoSector] varchar(10) NULL,
        [responsableSector] int NULL,
        CONSTRAINT [pk_sectors] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.Users', 'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[Users] (
        [identificador] int IDENTITY(1,1) NOT NULL,
        [documento] varchar(20) NOT NULL,
        [nombre] varchar(150) NOT NULL,
        [apellido] varchar(150) NOT NULL,
        [email] varchar(150) NULL,
        [telefono] varchar(50) NULL,
        [direccion] varchar(250) NULL,
        [estado] varchar(20) NOT NULL,
        [foto] varbinary(MAX) NULL,
        [fotoDniFrente] varbinary(MAX) NULL,
        [fotoDniDorso] varbinary(MAX) NULL,
        [clave] varchar(250) NULL,
        [fechaAlta] datetime NOT NULL CONSTRAINT [DF__Users__fechaAlta__4F7CD00D] DEFAULT (DATEADD(HOUR,-3,SYSUTCDATETIME())),
        CONSTRAINT [pk_users] PRIMARY KEY ([identificador])
    );
END
GO

IF OBJECT_ID('dbo.chk_auctionRecords_comision', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords] ADD CONSTRAINT [chk_auctionRecords_comision] CHECK ([comision]>(0.01));
END
GO

IF OBJECT_ID('dbo.chk_auctionRecords_estadoPago', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords] ADD CONSTRAINT [chk_auctionRecords_estadoPago] CHECK ([estadoPago]='multa' OR [estadoPago]='rechazado' OR [estadoPago]='pagado' OR [estadoPago]='pendiente');
END
GO

IF OBJECT_ID('dbo.chk_auctionRecords_importe', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords] ADD CONSTRAINT [chk_auctionRecords_importe] CHECK ([importe]>(0.01));
END
GO

IF OBJECT_ID('dbo.chk_auctionRecords_retiro', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords] ADD CONSTRAINT [chk_auctionRecords_retiro] CHECK ([retiroPersonal]='no' OR [retiroPersonal]='si');
END
GO

IF OBJECT_ID('dbo.chk_auctions_categoria', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctions] ADD CONSTRAINT [chk_auctions_categoria] CHECK ([categoria]='platino' OR [categoria]='oro' OR [categoria]='plata' OR [categoria]='especial' OR [categoria]='comun');
END
GO

IF OBJECT_ID('dbo.chk_auctions_deposito', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctions] ADD CONSTRAINT [chk_auctions_deposito] CHECK ([tieneDeposito]='no' OR [tieneDeposito]='si');
END
GO

IF OBJECT_ID('dbo.chk_auctions_estado', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctions] ADD CONSTRAINT [chk_auctions_estado] CHECK ([estado]='cancelada' OR [estado]='cerrada' OR [estado]='en_curso' OR [estado]='abierta' OR [estado]='programada');
END
GO

IF OBJECT_ID('dbo.chk_auctions_moneda', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctions] ADD CONSTRAINT [chk_auctions_moneda] CHECK ([moneda]='dolares' OR [moneda]='pesos');
END
GO

IF OBJECT_ID('dbo.chk_auctions_seguridad', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctions] ADD CONSTRAINT [chk_auctions_seguridad] CHECK ([seguridadPropia]='no' OR [seguridadPropia]='si');
END
GO

IF OBJECT_ID('dbo.chk_bids_confirmado', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Bids] ADD CONSTRAINT [chk_bids_confirmado] CHECK ([confirmado]='no' OR [confirmado]='si');
END
GO

IF OBJECT_ID('dbo.chk_bids_ganador', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Bids] ADD CONSTRAINT [chk_bids_ganador] CHECK ([ganador]='no' OR [ganador]='si');
END
GO

IF OBJECT_ID('dbo.chk_bids_importe', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Bids] ADD CONSTRAINT [chk_bids_importe] CHECK ([importe]>(0.01));
END
GO

IF OBJECT_ID('dbo.chk_catalogItems_comision', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[CatalogItems] ADD CONSTRAINT [chk_catalogItems_comision] CHECK ([comision]>(0.01));
END
GO

IF OBJECT_ID('dbo.chk_catalogItems_precioBase', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[CatalogItems] ADD CONSTRAINT [chk_catalogItems_precioBase] CHECK ([precioBase]>(0.01));
END
GO

IF OBJECT_ID('dbo.chk_catalogItems_subastado', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[CatalogItems] ADD CONSTRAINT [chk_catalogItems_subastado] CHECK ([subastado]='no' OR [subastado]='si');
END
GO

IF OBJECT_ID('dbo.chk_catalogItems_vendido', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[CatalogItems] ADD CONSTRAINT [chk_catalogItems_vendido] CHECK ([vendido]='no' OR [vendido]='si');
END
GO

IF OBJECT_ID('dbo.chk_clients_admitido', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Clients] ADD CONSTRAINT [chk_clients_admitido] CHECK ([admitido]='no' OR [admitido]='si');
END
GO

IF OBJECT_ID('dbo.chk_clients_categoria', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Clients] ADD CONSTRAINT [chk_clients_categoria] CHECK ([categoria]='platino' OR [categoria]='oro' OR [categoria]='plata' OR [categoria]='especial' OR [categoria]='comun');
END
GO

IF OBJECT_ID('dbo.chk_fines_monto', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Fines] ADD CONSTRAINT [chk_fines_monto] CHECK ([monto]>(0));
END
GO

IF OBJECT_ID('dbo.chk_fines_pagada', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Fines] ADD CONSTRAINT [chk_fines_pagada] CHECK ([pagada]='no' OR [pagada]='si');
END
GO

IF OBJECT_ID('dbo.chk_insurances_combinada', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Insurances] ADD CONSTRAINT [chk_insurances_combinada] CHECK ([polizaCombinada]='no' OR [polizaCombinada]='si');
END
GO

IF OBJECT_ID('dbo.chk_insurances_importe', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Insurances] ADD CONSTRAINT [chk_insurances_importe] CHECK ([importe]>(0));
END
GO

IF OBJECT_ID('dbo.chk_notifications_leida', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Notifications] ADD CONSTRAINT [chk_notifications_leida] CHECK ([leida]='no' OR [leida]='si');
END
GO

IF OBJECT_ID('dbo.chk_ownerBank_ext', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[OwnerBankAccounts] ADD CONSTRAINT [chk_ownerBank_ext] CHECK ([esExterior]='no' OR [esExterior]='si');
END
GO

IF OBJECT_ID('dbo.chk_ownerBank_moneda', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[OwnerBankAccounts] ADD CONSTRAINT [chk_ownerBank_moneda] CHECK ([moneda]='dolares' OR [moneda]='pesos');
END
GO

IF OBJECT_ID('dbo.chk_owners_cr', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Owners] ADD CONSTRAINT [chk_owners_cr] CHECK ([calificacionRiesgo]=(6) OR [calificacionRiesgo]=(5) OR [calificacionRiesgo]=(4) OR [calificacionRiesgo]=(3) OR [calificacionRiesgo]=(2) OR [calificacionRiesgo]=(1));
END
GO

IF OBJECT_ID('dbo.chk_owners_vf', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Owners] ADD CONSTRAINT [chk_owners_vf] CHECK ([verificacionFinanciera]='no' OR [verificacionFinanciera]='si');
END
GO

IF OBJECT_ID('dbo.chk_owners_vj', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Owners] ADD CONSTRAINT [chk_owners_vj] CHECK ([verificacionJudicial]='no' OR [verificacionJudicial]='si');
END
GO

IF OBJECT_ID('dbo.chk_payment_extranjera', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[PaymentMethods] ADD CONSTRAINT [chk_payment_extranjera] CHECK ([esExtranjera]='no' OR [esExtranjera]='si');
END
GO

IF OBJECT_ID('dbo.chk_payment_moneda', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[PaymentMethods] ADD CONSTRAINT [chk_payment_moneda] CHECK ([moneda]='dolares' OR [moneda]='pesos');
END
GO

IF OBJECT_ID('dbo.chk_payment_tipo', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[PaymentMethods] ADD CONSTRAINT [chk_payment_tipo] CHECK ([tipo]='cheque_certificado' OR [tipo]='tarjeta_credito' OR [tipo]='cuenta_bancaria');
END
GO

IF OBJECT_ID('dbo.chk_payment_verificado', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[PaymentMethods] ADD CONSTRAINT [chk_payment_verificado] CHECK ([verificado]='no' OR [verificado]='si');
END
GO

IF OBJECT_ID('dbo.chk_products_declaracion', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products] ADD CONSTRAINT [chk_products_declaracion] CHECK ([declaracionPropiedad]='no' OR [declaracionPropiedad]='si');
END
GO

IF OBJECT_ID('dbo.chk_products_disponible', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products] ADD CONSTRAINT [chk_products_disponible] CHECK ([disponible]='no' OR [disponible]='si');
END
GO

IF OBJECT_ID('dbo.chk_products_estadoAprobacion', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products] ADD CONSTRAINT [chk_products_estadoAprobacion] CHECK ([estadoAprobacion]='incluido_subasta' OR [estadoAprobacion]='rechazado_usuario' OR [estadoAprobacion]='aceptado_usuario' OR [estadoAprobacion]='propuesta_enviada' OR [estadoAprobacion]='pendiente_inspeccion' OR [estadoAprobacion]='rechazado' OR [estadoAprobacion]='aceptado' OR [estadoAprobacion]='pendiente');
END
GO

IF OBJECT_ID('dbo.chk_products_origen', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products] ADD CONSTRAINT [chk_products_origen] CHECK ([origenLicito]='no' OR [origenLicito]='si');
END
GO

IF OBJECT_ID('dbo.chk_users_estado', 'C') IS NULL
BEGIN
    ALTER TABLE [dbo].[Users] ADD CONSTRAINT [chk_users_estado] CHECK ([estado]='rechazado' OR [estado]='bloqueado' OR [estado]='inactivo' OR [estado]='activo' OR [estado]='pendiente');
END
GO

IF OBJECT_ID('dbo.fk_attendees_auctions', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Attendees]
    ADD CONSTRAINT [fk_attendees_auctions] FOREIGN KEY ([subasta])
    REFERENCES [dbo].[Auctions] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_attendees_clients', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Attendees]
    ADD CONSTRAINT [fk_attendees_clients] FOREIGN KEY ([cliente])
    REFERENCES [dbo].[Clients] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctioneers_users', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctioneers]
    ADD CONSTRAINT [fk_auctioneers_users] FOREIGN KEY ([identificador])
    REFERENCES [dbo].[Users] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctionRecords_auctions', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords]
    ADD CONSTRAINT [fk_auctionRecords_auctions] FOREIGN KEY ([subasta])
    REFERENCES [dbo].[Auctions] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctionRecords_clients', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords]
    ADD CONSTRAINT [fk_auctionRecords_clients] FOREIGN KEY ([cliente])
    REFERENCES [dbo].[Clients] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctionRecords_owners', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords]
    ADD CONSTRAINT [fk_auctionRecords_owners] FOREIGN KEY ([duenio])
    REFERENCES [dbo].[Owners] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctionRecords_paymentMethods', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords]
    ADD CONSTRAINT [fk_auctionRecords_paymentMethods] FOREIGN KEY ([medioPago])
    REFERENCES [dbo].[PaymentMethods] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctionRecords_products', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[AuctionRecords]
    ADD CONSTRAINT [fk_auctionRecords_products] FOREIGN KEY ([producto])
    REFERENCES [dbo].[Products] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_auctions_auctioneers', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Auctions]
    ADD CONSTRAINT [fk_auctions_auctioneers] FOREIGN KEY ([subastador])
    REFERENCES [dbo].[Auctioneers] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_bids_attendees', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Bids]
    ADD CONSTRAINT [fk_bids_attendees] FOREIGN KEY ([asistente])
    REFERENCES [dbo].[Attendees] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_bids_catalogItems', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Bids]
    ADD CONSTRAINT [fk_bids_catalogItems] FOREIGN KEY ([item])
    REFERENCES [dbo].[CatalogItems] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_catalogItems_catalogs', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[CatalogItems]
    ADD CONSTRAINT [fk_catalogItems_catalogs] FOREIGN KEY ([catalogo])
    REFERENCES [dbo].[Catalogs] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_catalogItems_products', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[CatalogItems]
    ADD CONSTRAINT [fk_catalogItems_products] FOREIGN KEY ([producto])
    REFERENCES [dbo].[Products] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_catalogs_auctions', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Catalogs]
    ADD CONSTRAINT [fk_catalogs_auctions] FOREIGN KEY ([subasta])
    REFERENCES [dbo].[Auctions] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_catalogs_employees', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Catalogs]
    ADD CONSTRAINT [fk_catalogs_employees] FOREIGN KEY ([responsable])
    REFERENCES [dbo].[Employees] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_clients_countries', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Clients]
    ADD CONSTRAINT [fk_clients_countries] FOREIGN KEY ([numeroPais])
    REFERENCES [dbo].[Countries] ([numero]);
END
GO

IF OBJECT_ID('dbo.fk_clients_employees', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Clients]
    ADD CONSTRAINT [fk_clients_employees] FOREIGN KEY ([verificador])
    REFERENCES [dbo].[Employees] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_clients_users', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Clients]
    ADD CONSTRAINT [fk_clients_users] FOREIGN KEY ([identificador])
    REFERENCES [dbo].[Users] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_employees_sectors', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Employees]
    ADD CONSTRAINT [fk_employees_sectors] FOREIGN KEY ([sector])
    REFERENCES [dbo].[Sectors] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_employees_users', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Employees]
    ADD CONSTRAINT [fk_employees_users] FOREIGN KEY ([identificador])
    REFERENCES [dbo].[Users] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_fines_auctions', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Fines]
    ADD CONSTRAINT [fk_fines_auctions] FOREIGN KEY ([subasta])
    REFERENCES [dbo].[Auctions] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_fines_clients', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Fines]
    ADD CONSTRAINT [fk_fines_clients] FOREIGN KEY ([cliente])
    REFERENCES [dbo].[Clients] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_insurances_owners', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Insurances]
    ADD CONSTRAINT [fk_insurances_owners] FOREIGN KEY ([duenio])
    REFERENCES [dbo].[Owners] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_notifications_clients', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Notifications]
    ADD CONSTRAINT [fk_notifications_clients] FOREIGN KEY ([cliente])
    REFERENCES [dbo].[Clients] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_ownerBankAccounts_owners', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[OwnerBankAccounts]
    ADD CONSTRAINT [fk_ownerBankAccounts_owners] FOREIGN KEY ([duenio])
    REFERENCES [dbo].[Owners] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_owners_countries', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Owners]
    ADD CONSTRAINT [fk_owners_countries] FOREIGN KEY ([numeroPais])
    REFERENCES [dbo].[Countries] ([numero]);
END
GO

IF OBJECT_ID('dbo.fk_owners_employees', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Owners]
    ADD CONSTRAINT [fk_owners_employees] FOREIGN KEY ([verificador])
    REFERENCES [dbo].[Employees] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_owners_users', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Owners]
    ADD CONSTRAINT [fk_owners_users] FOREIGN KEY ([identificador])
    REFERENCES [dbo].[Users] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_paymentMethods_clients', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[PaymentMethods]
    ADD CONSTRAINT [fk_paymentMethods_clients] FOREIGN KEY ([cliente])
    REFERENCES [dbo].[Clients] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_photos_products', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Photos]
    ADD CONSTRAINT [fk_photos_products] FOREIGN KEY ([producto])
    REFERENCES [dbo].[Products] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_products_employees', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products]
    ADD CONSTRAINT [fk_products_employees] FOREIGN KEY ([revisor])
    REFERENCES [dbo].[Employees] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_products_insurances', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products]
    ADD CONSTRAINT [fk_products_insurances] FOREIGN KEY ([seguro])
    REFERENCES [dbo].[Insurances] ([nroPoliza]);
END
GO

IF OBJECT_ID('dbo.fk_products_owners', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Products]
    ADD CONSTRAINT [fk_products_owners] FOREIGN KEY ([duenio])
    REFERENCES [dbo].[Owners] ([identificador]);
END
GO

IF OBJECT_ID('dbo.fk_sectors_employees', 'F') IS NULL
BEGIN
    ALTER TABLE [dbo].[Sectors]
    ADD CONSTRAINT [fk_sectors_employees] FOREIGN KEY ([responsableSector])
    REFERENCES [dbo].[Employees] ([identificador]);
END
GO
