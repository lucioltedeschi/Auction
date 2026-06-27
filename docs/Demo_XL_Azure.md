# Demo XL en Azure SQL

El entorno remoto incluye un set idempotente pensado para una demostración extensa.

## Contenido incorporado

- 8 clientes adicionales activos, con categorías común, plata, oro y platino.
- 6 subastas temáticas en estados `programada`, `abierta`, `en_curso` y `cerrada`.
- 30 productos completos y 24 lotes publicados.
- 180 fotografías optimizadas almacenadas como `varbinary(MAX)`.
- Asistentes, pujas progresivas, ganadores, compras pagadas y pendientes.
- Medios de pago verificados y uno pendiente para revisión interna.
- Multas pagadas y pendientes.
- 32 notificaciones, con avisos leídos y no leídos.
- 6 consignaciones en distintos puntos del flujo administrativo.

Las seis fotografías base fueron generadas para este proyecto y se encuentran en
`database/demo-assets/`. El seeder rota esas imágenes para que cada producto tenga
las seis fotos requeridas sin inflar innecesariamente el repositorio.

## Usuarios adicionales

Todos usan clave `1234`:

- `51000001` · Valentina Rossi · oro
- `51000002` · Julián Paz · platino
- `51000003` · Camila Suárez · plata
- `51000004` · Tomás Lagos · común
- `51000005` · Renata Ibarra · oro
- `51000006` · Bruno Méndez · plata
- `51000007` · Elena Ferrer · platino
- `51000008` · Nicolás Vega · común; medio de pago pendiente

## Ejecución segura

```powershell
npm.cmd run db:seed:demo-xl
npm.cmd run db:verify:azure
```

El proceso es idempotente y transaccional: puede repetirse sin duplicar registros y,
si falla una dependencia, revierte la ejecución completa.

## Administración

El panel interno mantiene las operaciones de negocio:

- aprobar o rechazar usuarios y medios de pago;
- revisar consignaciones y enviar propuestas;
- asignar productos aceptados a subastas;
- cerrar lotes y generar compras;
- crear multas;
- crear, listar, editar, cancelar y eliminar subastas vacías.

Al crear una subasta desde el nuevo ABM se crea también su catálogo. Una subasta con
lotes, asistentes, ventas o multas no puede eliminarse; debe marcarse `cancelada` para
preservar la trazabilidad.
