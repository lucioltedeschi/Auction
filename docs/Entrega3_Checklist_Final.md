# Entrega 3 - Checklist final

## Codigo y base

- Backend levanta con `npm start`.
- Health check responde en `/api/test`.
- Android compila con `.\gradlew.bat :app:assembleDebug`.
- APK generado en `app/build/outputs/apk/debug/app-debug.apk`.
- Base nueva preparada con:
  1. `database/CREAR_BASE.sql`
  2. `database/DATOS_DEMO_BASE.sql`
  3. `database/ENTREGA3_CORRECCIONES.sql`
  4. `database/ENTREGA3_DEMO_SUBASTAS.sql`
  5. `database/DATOS_DEMO_MULTA.sql` opcional

## Backend online

- Crear servicio Node.js.
- Comando de instalacion: `npm install`.
- Comando de inicio: `npm start`.
- Health check: `/api/test`.
- Variables requeridas:

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

## Android

- Recompilar el APK con `-PAPI_BASE_URL=https://URL_PUBLICA`.
- Probar login con:
  - Admin: `20000111` / `1234`
  - Cliente: `30123456` / `1234`

## Demo principal

- Cliente consigna bien con 6 fotos.
- Admin revisa consignacion, ve fotos/datos/precio sugerido y envia propuesta.
- Cliente acepta condiciones.
- Admin asigna producto aceptado a una subasta.
- Cliente entra a subasta, ve catalogo y puja.
- Admin cierra item y se genera compra.
- Cliente ve compra en Compras y Pagos.

## Casos de error para mostrar

- Login incorrecto.
- Usuario pendiente.
- Usuario con multa pendiente.
- Puja menor al minimo.
- Puja mayor al maximo.
- Intento de entrar a dos subastas a la vez.
- Consignacion sin 6 fotos.
- Rechazo de condiciones de consignacion.

## Pendiente externo

- Backend publicado en Render: `https://auct-io-api.onrender.com`.
- Base remota operativa en Azure SQL.
- APK configurado para consumir la URL publica de Render.
- Prueba publica automatizada disponible con `npm run test:smoke:public`.
