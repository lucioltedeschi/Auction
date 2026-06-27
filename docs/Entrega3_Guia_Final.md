# Entrega 3 - Guia final de instalacion y demo

## Objetivo

Demostrar la app completa funcionando con frontend Android, backend REST y SQL Server, cubriendo comprador, vendedor y panel interno.

## Preparacion de base

1. Crear/cargar la base `auction`.
2. Si la base esta vacia, ejecutar `database/CREAR_BASE.sql` para crear la estructura.
3. Ejecutar `database/DATOS_DEMO_BASE.sql` para usuarios, roles, medios de pago y seguros base.
4. Ejecutar obligatoriamente:

```sql
database/ENTREGA3_CORRECCIONES.sql
```

Ese script agrega los estados de consignacion, condiciones propuestas y `precioBaseSugerido`.

5. Ejecutar `database/ENTREGA3_DEMO_SUBASTAS.sql` para subastas, catalogos, lotes y fotos demo.
6. Ejecutar `database/DATOS_DEMO_MULTA.sql` si se quiere probar bloqueo por multa con `31888777`.

## Backend local

Desde la raiz del proyecto:

```bash
npm install
copy .env.example .env
npm start
```

Probar:

```text
http://localhost:3000/api/test
```

## Backend online

Para entrega final, publicar el backend y configurar estas variables:

```env
PORT=3000
DB_USER=...
DB_PASSWORD=...
DB_SERVER=...
DB_PORT=1433
DB_NAME=auction
DB_ENCRYPT=true
DB_TRUST_CERT=false
COMPANY_CLIENT_ID=9000007
```

La URL publica se inyecta al compilar el APK, sin modificar codigo fuente.

## APK

Compilar:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=https://URL_PUBLICA
```

Archivo a entregar/probar:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Usuarios

- Admin: `20000111` / `1234`
- Cliente vendedor/comprador: `30123456` / `1234`
- Cliente alternativo: `30999888` / `1234`
- Cliente con multa: `31888777` / `1234`
- Cliente interno empresa: `90000007` / `1234` (no se usa en demo normal; registra compras sin pujas; ID tecnico `9000007`)

## Demo principal recomendada

### 1. Cliente consigna un bien

Usuario: `30123456 / 1234`

1. Entrar a `CONSIGNAR`.
2. Completar titulo, descripcion, historia y precio base sugerido.
3. Cargar 6 fotos.
4. Marcar propiedad y origen licito.
5. Enviar solicitud.

Resultado esperado:

- Estado `Pendiente de inspeccion`.
- Se ve `Precio sugerido por vos`.

### 2. Admin envia propuesta

Usuario: `20000111 / 1234`

1. Entrar a panel interno.
2. Ver `Consignaciones pendientes`.
3. Revisar foto, datos e importe sugerido.
4. Enviar propuesta con precio base y comision.

Resultado esperado:

- El producto pasa a `Propuesta enviada`.
- El cliente todavia debe aceptar.

### 3. Cliente acepta condiciones

Usuario: `30123456 / 1234`

1. Entrar a `CONSIGNAR`.
2. Revisar `Mis consignaciones`.
3. Aceptar la propuesta.

Resultado esperado:

- Estado `Aceptado por usuario`.
- La app explica que admin ya puede asignar a subasta.

### 4. Admin asigna a subasta

Usuario: `20000111 / 1234`

1. Entrar a panel interno.
2. Tocar `ASIGNAR A SUBASTA`.
3. Cargar ID de subasta, ID de producto, precio base y comision.

Resultado esperado:

- Producto asignado.
- Estado del producto `Incluido en subasta`.

### 5. Cliente ve subasta y puja

Usuario: `30123456 / 1234`

1. Entrar a `SUBASTAS`.
2. Abrir la subasta usada.
3. Ver catalogo.
4. Abrir puja de un lote habilitado.
5. Ingresar importe entre minimo y maximo.

Resultado esperado:

- La puja queda registrada.
- La app vuelve al catalogo para seguir la subasta.

### 6. Admin cierra item

Usuario: `20000111 / 1234`

1. Entrar a panel interno.
2. Cerrar item con ID de subasta e ID de item.

Resultado esperado:

- Se marca ganador.
- Se genera compra.
- El cliente ve la compra en `COMPRAS Y PAGOS`.

Si no hubo pujas para el lote:

- Se genera una compra de la empresa al precio base.
- El producto pasa a pertenecer al cliente interno empresa `COMPANY_CLIENT_ID=9000007`.
- El vendedor recibe una notificacion si tambien existe como cliente.

## Errores a mostrar si piden manejo de errores

- Login incorrecto.
- Usuario pendiente.
- Medio de pago no verificado.
- Multa pendiente.
- Categoria insuficiente.
- Puja menor al minimo.
- Puja mayor al maximo.
- Intentar entrar a dos subastas a la vez.
- Consignacion sin 6 fotos.
- Rechazo/aceptacion de propuesta.

## Estado de publicación

- Backend online: `https://auct-io-api.onrender.com`.
- Base de datos: Azure SQL.
- Android usa la URL pública por defecto y puede sobrescribirse con `-PAPI_BASE_URL`.
- Validación automática pública: `npm run test:smoke:public`.
- `database/CREAR_BASE.sql` crea estructura; los datos demo están separados en `DATOS_DEMO_BASE.sql`, `ENTREGA3_DEMO_SUBASTAS.sql` y `DATOS_DEMO_MULTA.sql`.
