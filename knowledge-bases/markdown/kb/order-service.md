# order-service

> Creates customer orders, reserves stock in inventory-service, and emits order.created.

- **System:** commerce · **Domain:** ordering · **Owner:** team-ordering

## Inbound APIs

| Method | Path | Summary | Request | Response | Errors |
|---|---|---|---|---|---|
| POST | `/api/orders` | Create an order and synchronously reserve stock. | CreateOrderRequest | OrderResponse | 409 OUT_OF_STOCK, 404 NOT_FOUND |
| GET | `/api/orders/{id}` | Fetch an order by id. | — | OrderResponse | 404 NOT_FOUND |

## Outbound calls

| Target service | Method | Path | Request | Response | Call site |
|---|---|---|---|---|---|
| inventory-service | POST | `/api/inventory/reserve` | ReserveRequest | ReserveResponse | `com.ganesh.commerce.order.client.InventoryClient#reserve` |

## Kafka

- **Produces:** order.created (OrderCreatedEvent)
- **Consumes:** —

## Data

- **DB:** orders (postgres) — tables: orders
- **CACHE:** plans (caffeine) — regions: plans

---

# order-service — narrative

Owns the **Order** aggregate (`orders` table). Entry point for the digital, retail, and
call-center channels to place an order.

## Business rules
- An order is `CREATED`, then `CONFIRMED` once inventory-service confirms the reservation.
- If inventory returns `available = false`, the order is **rolled back** and the API returns
  `409 OUT_OF_STOCK`. Nothing is persisted in that case.

## Integration gotchas
- The reservation call is **synchronous and in the same transaction** as the order insert —
  a slow inventory-service directly affects `POST /api/orders` latency.
- `order.created` carries `reservedQty`; consumers (inventory-service) rely on it. Treat the
  topic schema as a published contract — additive changes only.

> Machine-readable detail lives in `catalog-info.yaml`, `openapi.yaml`, `asyncapi.yaml`.
> This file is only the "why" a human needs.
