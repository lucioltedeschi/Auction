# Orden de ejecucion - Entrega 3

Usar este orden para preparar una base nueva o una base remota limpia:

1. `CREAR_BASE.sql`
2. `DATOS_DEMO_BASE.sql`
3. `ENTREGA3_CORRECCIONES.sql`
4. `ENTREGA3_DEMO_SUBASTAS.sql`
5. `DATOS_DEMO_MULTA.sql` opcional para probar multa pendiente con `31888777 / 1234`

`ENTREGA3_DEMO_SUBASTAS.sql` es el seed recomendado para la entrega final. Es idempotente y puede volver a ejecutarse sin duplicar subastas, catalogos, lotes, asistentes ni fotos demo.

`NUEVAS_SUBASTAS.sql` queda como seed historico usado en entregas anteriores. No es el camino recomendado para Entrega 3.

## Usuarios demo

- Admin/verificador: `20000111` / `1234`
- Cliente comprador/vendedor: `30123456` / `1234`
- Cliente alternativo: `30999888` / `1234`
- Cliente con multa: `31888777` / `1234`
- Usuario pendiente: `40000111` / `1234`
- Cliente interno empresa: `90000007` / `1234`

## Validacion rapida

Con el backend levantado:

```text
GET http://localhost:3000/api/test
```

Para celular fisico en la misma red, la app debe apuntar a la IP de la PC en `app/src/main/java/com/example/clase4/ApiConfig.java`.
