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
| Varios lotes simultáneos por catálogo | Cumple | Todos los lotes no cerrados pueden recibir pujas independientes dentro de una misma subasta. |
| Puja ascendente y confirmada por servidor | Cumple | El servidor valida mejor oferta, mínimo y máximo de cada lote antes de insertar. |
| Incremento mínimo 1% y máximo 20% | Cumple | Aplicado sobre precio base respecto de la mejor oferta; excepción oro/platino. |
| Una sola subasta conectada por cliente | Cumple | Sesión activa; permite cambiar si aún no ofertó y conserva el vínculo si ya pujó. |
| Moneda única y compatibilidad para USD | Cumple | Solo acepta cuenta bancaria, tarjeta internacional o cheque compatible y verificado. |
| Límite de cheque certificado | Cumple | La puja y el cierre controlan monto disponible; se descuenta al adjudicar. |
| Conservación de todas las pujas | Cumple | Historial persistente por lote; cada tarjeta muestra además las tres ofertas más recientes sin exponer identidad. |
| Temporizador por lote | Cumple | Cada lote corre en forma independiente: comienza al abrir y una puja válida reinicia únicamente ese reloj según `duracionItemMinutos`. |
| Lote sin ofertas comprado por empresa | Cumple | Al vencer el reloj desde la apertura, se cierra automáticamente al precio base y se avisa al consignante. |
| Venta, comisión, envío y retiro personal | Cumple | Registro de compra con desglose; el pago permite elegir medio. |
| Impago, plazo 72 h y multa 10% | Cumple | Bloqueo de nuevas pujas por vencimiento y opción administrativa automática del 10%. |
| Avisos de acciones relevantes | Cumple | Puja, perfil, medios, consignación, propuestas, adjudicación, pago y multas generan avisos. |
| Estadísticas del cliente | Cumple | Subastas, lotes, pujas, ganados, importes, consignaciones, avisos y categorías. |
| ABM administrativo | Cumple | Subastas completas, verificaciones, propuestas, asignación, cierre y multas sin IDs manuales. |
| Tiempo real | Cumple | WebSocket por subasta con reconexión; sincroniza relojes, últimas ofertas, importes y liderazgos de todos los lotes sin refrescar. |
| Video de la subasta | No requerido | La consigna declara expresamente que la transmisión de video queda fuera del alcance. |

## Integraciones representadas en la demo

La emisión real de pólizas, el correo transaccional, la acreditación bancaria y la transferencia al consignante se representan mediante estados persistentes y avisos. Para producción requieren credenciales y contratos con proveedores externos; no se simulan claves bancarias ni se procesan fondos reales en esta entrega académica.

## Riesgos controlados

- Las decisiones económicas se recalculan en el servidor aunque se altere la app.
- Las acciones internas requieren token de empleado.
- Un medio no verificado no habilita pujas ni pagos.
- Un cliente con multa o compra vencida no puede volver a pujar.
- El historial ya no agrupa varios artículos de una misma subasta: cada lote se visualiza por separado.
- Una puja vencida se rechaza también en el servidor, aunque la pantalla del usuario todavía no haya recibido el cierre.

## Escenario de prueba actualizado

Azure contiene 9 subastas demo, 36 lotes, 252 fotografías y más de 110 pujas. El escenario
en vivo recomendado es la subasta `Fotografía & Tecnología` (45 minutos por lote): la prueba
automática confirmó dos clientes simultáneos, cuatro contadores independientes, cuatro
historiales y el reinicio del reloj después de una puja.
