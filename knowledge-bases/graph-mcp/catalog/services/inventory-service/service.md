# inventory-service — narrative

Owns stock (`inventory` table) and holds (`reservations` table). Called synchronously by
order-service and reconciles asynchronously off `order.created`.

## Business rules
- `reserve` succeeds only when `availableQuantity >= requested`. Otherwise it returns
  `available = false` (HTTP 200) — it does **not** throw. order-service turns that into 409.
- A successful reserve decrements stock, writes a `Reservation`, evicts the `sku-availability`
  cache for that SKU, and emits `inventory.reserved`.

## Integration gotchas
- The `sku-availability` cache (TTL 30s) can briefly serve stale stock; `reserve` is the
  source of truth and always reads/writes the row transactionally.
- Consumes `order.created` — depends on its `reservedQty` field. A producer-side schema change
  to that topic lands here.

> Machine-readable detail lives in `catalog-info.yaml`, `openapi.yaml`, `asyncapi.yaml`.
