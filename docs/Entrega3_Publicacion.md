# Entrega 3 - Publicacion

## 1. Base Azure SQL

1. Crear una base Azure SQL Database y conservar su nombre exacto, por ejemplo
   `auction` o `db-auction`.
2. Elegir autenticacion SQL y guardar el usuario administrador, la clave y el
   nombre completo del servidor, por ejemplo `servidor.database.windows.net`.
3. Agregar temporalmente la IP del equipo que ejecutara los scripts.
4. Copiar `.env.example` como `.env` y completar `DB_USER`, `DB_PASSWORD`,
   `DB_SERVER` y `DB_NAME` con los datos exactos de Azure.
5. Ejecutar `npm run db:setup:azure`. El cargador adapta los scripts de SQL
   Server para ejecutarlos dentro de la base que Azure ya creo.

## 2. Backend Render

El archivo `render.yaml` deja preparado un Web Service gratuito.

1. Subir los cambios a GitHub.
2. En Render, crear un Blueprint desde el repositorio.
   El servicio queda configurado en Frankfurt para reducir la distancia con la
   region de Azure disponible para esta suscripcion estudiantil.
3. Completar los secretos solicitados:
   - `DB_USER`: usuario administrador de Azure SQL.
   - `DB_PASSWORD`: clave de Azure SQL.
   - `DB_SERVER`: servidor terminado en `.database.windows.net`.
4. Confirmar que `https://NOMBRE.onrender.com/api/health` responde 200.
5. Abrir `https://NOMBRE.onrender.com/api/test` y comprobar que tambien
   responde 200 y muestra la fecha del servidor SQL.

## 3. APK de entrega

Desde PowerShell, reemplazar la URL por la asignada por Render:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug -PAPI_BASE_URL=https://NOMBRE.onrender.com
```

El archivo final queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalarlo en un celular y verificar, usando datos moviles o una red distinta:

1. Login cliente `30123456 / 1234`.
2. Login administrador `20000111 / 1234`.
3. Consignacion con seis fotos y aceptacion de propuesta.
4. Asignacion a subasta, puja, cierre y compra generada.

## 4. Documentacion

Reemplazar el texto pendiente de `docs/Correcciones_Entrega1.md` por el enlace
real del archivo compartido de Figma y verificar que cualquier evaluador con el
enlace pueda abrirlo.
