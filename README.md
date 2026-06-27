# AUCT.IO - Sistema de Subastas

Aplicacion Android + API REST + SQL Server para gestionar subastas, pujas, compras, multas y consignacion de bienes.

## Estructura

- `app/`: frontend Android nativo.
- `backend/`: API REST Node.js/Express.
- `database/`: scripts SQL y migraciones.
- `docs/`: consignas, correcciones, guias de demo y auditoria.

## Backend

1. Copiar `.env.example` a `.env`.
2. Ajustar credenciales SQL Server:

```env
PORT=3000
DB_USER=sa
DB_PASSWORD=1234
DB_SERVER=localhost
DB_PORT=1433
DB_NAME=auction
DB_ENCRYPT=false
DB_TRUST_CERT=true
COMPANY_CLIENT_ID=9000007
```

3. Instalar dependencias desde la raiz:

```bash
npm install
```

4. Levantar API:

```bash
npm start
```

Health check:

```text
GET /api/test
```

## Android

La URL del backend se define al compilar. Sin parametro usa
`http://10.0.2.2:3000`, apropiado para el emulador Android.

Compilar APK debug:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

Para un celular en la red local:

```powershell
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=http://IP_DE_LA_PC:3000
```

Para Entrega 3 con backend online:

```powershell
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=https://URL_PUBLICA
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Usuarios Demo

- Admin/verificador: `20000111` / `1234`
- Cliente habilitado: `30123456` / `1234`
- Cliente habilitado: `30999888` / `1234`
- Cliente con multa pendiente: `31888777` / `1234`
- Usuario pendiente precargado: `40000111` / `1234`
- Cliente interno empresa: `90000007` / `1234` (solo para registrar compras sin pujas; ID tecnico `9000007`)

## Documentacion De Entrega

- `docs/Estado_Entrega3_Auditoria.md`: cobertura de entregas 1, 2 y 3.
- `docs/Entrega3_Guia_Final.md`: instalacion, publicacion y guion de demo.
- `docs/Entrega3_Checklist_Final.md`: checklist corta para cierre de entrega.
- `docs/Segunda_Entrega_Flujo_Demo.md`: flujo integrado usado en entrega 2.
- `database/ORDEN_EJECUCION_ENTREGA3.md`: orden de scripts SQL recomendado.
- `backend/swagger.yaml`: documentacion OpenAPI.

Orden recomendado de scripts SQL para una base nueva:

1. `database/CREAR_BASE.sql`
2. `database/DATOS_DEMO_BASE.sql`
3. `database/ENTREGA3_CORRECCIONES.sql`
4. `database/ENTREGA3_DEMO_SUBASTAS.sql`
5. `database/DATOS_DEMO_MULTA.sql`

`database/NUEVAS_SUBASTAS.sql` queda como seed historico de entrega 2; para Entrega 3 conviene usar `ENTREGA3_DEMO_SUBASTAS.sql` porque es idempotente y tiene datos demo pensados para esta entrega.

## Correcciones Entrega 2 Cubiertas

- Un usuario no puede conectarse a mas de una subasta a la vez.
- El vendedor no elige subasta preferida.
- El admin decide catalogo/subasta.
- El admin informa precio base y comision.
- El cliente acepta o rechaza condiciones.
- El producto no se asigna a subasta sin aceptacion final del usuario.
- Si un lote cierra sin pujas, la empresa queda registrada como compradora al precio base.
