# Estado de cobertura - Entrega 3

Fecha de revision: 2026-06-26

## Entrega 1

Cubierto en la app/API:
- Splash, login y registro KYC en dos pasos.
- Pantalla de usuario pendiente de verificacion.
- Panel interno para aprobar/rechazar usuarios y asignar categoria.
- Medios de pago: cuenta bancaria, tarjeta y cheque certificado.
- Verificacion interna de medios de pago.
- Home, subastas, catalogo, detalle de lote y puja.
- Estado vivo de subasta por SSE.
- Reglas de puja: minimo, maximo y excepcion oro/platino.
- Bloqueo de boton mientras se confirma la puja.
- Compras, pagos, multas e historial.
- Consignacion con minimo 6 fotos, declaracion de propiedad y origen licito.
- Swagger/OpenAPI en `backend/swagger.yaml`.

Pendiente documental:
- Pegar el link real de Figma en la documentacion si todavia no se entrego.

## Entrega 2

Correcciones cubiertas:
- Login/registro: OK segun devolucion.
- Restriccion de una sola subasta activa por usuario.
- El vendedor ya no elige subasta preferida al consignar.
- La decision de catalogo/subasta queda en admin.
- Admin envia propuesta con precio base y comision.
- Cliente acepta o rechaza condiciones.
- El producto solo puede asignarse a subasta si esta en `aceptado_usuario`.
- Estados de consignacion soportados: `pendiente_inspeccion`, `rechazado`, `propuesta_enviada`, `aceptado_usuario`, `rechazado_usuario`, `incluido_subasta`.

Mejoras agregadas:
- El admin ve fotos, datos e importe sugerido por el cliente.
- El importe sugerido se guarda en `Products.precioBaseSugerido`.
- Se muestran ubicacion de deposito y seguro cuando la empresa informa condiciones.

## Entrega 3

Cubierto funcionalmente:
- App Android compilable e instalable con APK debug.
- Backend y frontend integrados.
- Flujos principales de comprador, vendedor y admin.
- Validaciones visibles y mensajes modales para errores frecuentes.
- Pantallas pulidas en un unico idioma y con menos caracteres dependientes de fuente.
- Limpieza de rutas backend duplicadas para compras/historial.
- Si nadie puja por un lote, se genera una venta a cliente interno empresa por el precio base.
- Seed demo idempotente para Entrega 3 en `database/ENTREGA3_DEMO_SUBASTAS.sql`.

Pendiente para cierre de entrega:
- Publicar el backend en una URL accesible online y actualizar `ApiConfig.BASE_URL`.
- Entregar o indicar el APK generado para instalar en dispositivo.
- Confirmar que la base remota tenga ejecutado `database/ENTREGA3_CORRECCIONES.sql`.
- Si se arma una base desde cero, ejecutar `CREAR_BASE.sql`, `DATOS_DEMO_BASE.sql`, `ENTREGA3_CORRECCIONES.sql`, `ENTREGA3_DEMO_SUBASTAS.sql` y `DATOS_DEMO_MULTA.sql`.

## Flujo recomendado de demo

1. Cliente `30123456 / 1234` consigna un producto con 6 fotos y precio sugerido.
2. Admin `20000111 / 1234` revisa la consignacion, ve foto/datos/importe sugerido y envia propuesta.
3. Cliente acepta la propuesta.
4. Admin asigna el producto aceptado a una subasta.
5. Cliente entra a Subastas, abre catalogo y verifica el lote.
6. Cliente puja con un importe dentro del rango permitido.
7. Admin cierra item para generar compra y el cliente ve Compras/Pagos.
