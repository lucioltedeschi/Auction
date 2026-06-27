# Auditoría funcional — Entrega 3 Auct.io

Fecha de revisión: 27/06/2026

## Resultado ejecutivo

La aplicación cubre el recorrido demostrable de registro, verificación, medios de pago, catálogo, pujas, adjudicación, pago, consignación, avisos, multas, administración y estadísticas. Las reglas críticas se validan en el backend; no dependen solamente de la interfaz Android.

## Matriz de cumplimiento

| Regla de la consigna | Estado | Evidencia funcional |
|---|---|---|
| Registro en dos pasos y documentación | Cumple | Alta de usuario y cliente con frente/dorso de DNI; queda pendiente de revisión. |
| Aprobación y categoría por empresa | Cumple | Ficha administrativa completa y aprobación/rechazo en un toque. |
| Múltiples medios de pago verificados | Cumple | ABM del cliente, revisión administrativa y selección al pagar. |
| Categoría de subasta no superior a la del cliente | Cumple | Validación en listado y nuevamente al crear la puja. |
| Catálogo, precio base y fotografías | Cumple | Catálogo por lotes y galería; consignación exige al menos seis fotos. |
| Un solo lote habilitado por vez | Cumple | La interfaz muestra Esperando turno y el servidor rechaza cualquier puja sobre un lote no activo. |
| Puja ascendente y confirmada por servidor | Cumple | El servidor valida lote activo, mejor oferta, mínimo y máximo antes de insertar. |
| Incremento mínimo 1% y máximo 20% | Cumple | Aplicado sobre precio base respecto de la mejor oferta; excepción oro/platino. |
| Una sola subasta conectada por cliente | Cumple | Sesión activa; permite cambiar si aún no ofertó y conserva el vínculo si ya pujó. |
| Moneda única y compatibilidad para USD | Cumple | Solo acepta cuenta bancaria, tarjeta internacional o cheque compatible y verificado. |
| Límite de cheque certificado | Cumple | La puja y el cierre controlan monto disponible; se descuenta al adjudicar. |
| Conservación de todas las pujas | Cumple | Historial persistente por lote, ordenado por fecha/hora. |
| Lote sin ofertas comprado por empresa | Cumple | Cierre automático al precio base y aviso al consignante. |
| Venta, comisión, envío y retiro personal | Cumple | Registro de compra con desglose; el pago permite elegir medio. |
| Impago, plazo 72 h y multa 10% | Cumple | Bloqueo de nuevas pujas por vencimiento y opción administrativa automática del 10%. |
| Avisos de acciones relevantes | Cumple | Puja, perfil, medios, consignación, propuestas, adjudicación, pago y multas generan avisos. |
| Estadísticas del cliente | Cumple | Subastas, lotes, pujas, ganados, importes, consignaciones, avisos y categorías. |
| ABM administrativo | Cumple | Subastas completas, verificaciones, propuestas, asignación, cierre y multas sin IDs manuales. |
| Tiempo real | Cumple | SSE cada 2 s con reconexión; actualiza mejor oferta, catálogo, liderazgo e historial sin refrescar. |
| Video de la subasta | No requerido | La consigna declara expresamente que la transmisión de video queda fuera del alcance. |

## Integraciones representadas en la demo

La emisión real de pólizas, el correo transaccional, la acreditación bancaria y la transferencia al consignante se representan mediante estados persistentes y avisos. Para producción requieren credenciales y contratos con proveedores externos; no se simulan claves bancarias ni se procesan fondos reales en esta entrega académica.

## Riesgos controlados

- Las decisiones económicas se recalculan en el servidor aunque se altere la app.
- Las acciones internas requieren token de empleado.
- Un medio no verificado no habilita pujas ni pagos.
- Un cliente con multa o compra vencida no puede volver a pujar.
- El historial ya no agrupa varios artículos de una misma subasta: cada lote se visualiza por separado.
