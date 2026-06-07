# inventory-service

> Tracks stock per SKU, reserves stock on request, consumes order.created and emits inventory.reserved.

- **System:** commerce · **Domain:** fulfillment · **Owner:** team-fulfillment

## Inbound APIs

| Method | Path | Summary | Request | Response | Errors |
|---|---|---|---|---|---|
| GET | `/api/inventory/{sku}` | Read current available stock for a SKU (cached). | — | InventoryResponse | 404 NOT_FOUND |
| POST | `/api/inventory/reserve` | Reserve stock for an order; returns available=false when insufficient. | ReserveRequest | ReserveResponse | 404 NOT_FOUND |

## Kafka

- **Produces:** inventory.reserved (InventoryReservedEvent)
- **Consumes:** order.created (OrderCreatedEvent)

## Data

- **DB:** inventory (postgres) — tables: inventory, reservations
- **CACHE:** sku-availability (caffeine) — regions: sku-availability

---

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
