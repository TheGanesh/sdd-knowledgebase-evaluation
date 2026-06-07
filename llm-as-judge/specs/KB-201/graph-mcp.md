# Feature → Story spec (KB-only) — KB-201

- **KB used:** graph-mcp (deterministic tools)
- **Tools called:** `impact_report(["order-service"])`, `get_dependents("topic:order.created")`, `get_api(...)`

## 1. Outcomes
- AC1: `POST /api/orders` reserves stock before confirming.
- AC2: out of stock → `409 OUT_OF_STOCK`, order not persisted.
- AC3: `order.created` carries `reservedQty`.

## 2. Cross-Service Impact  (the graded section)
- **Affected services:** `order-service`, `inventory-service`  *(impact_report.affectedCount = 2)*

| Service | API operation | Request → Response | Outbound calls | DB / Cache / Kafka |
|---|---|---|---|---|
| `order-service` | `POST /api/orders` | `CreateOrderRequest{customerId,sku,quantity}` → `OrderResponse(+reservationId)`; new `409 OUT_OF_STOCK` | → `inventory-service POST /api/inventory/reserve` `ReserveRequest` → `ReserveResponse` | topic:`order.created` **(+reservedQty)** |
| `inventory-service` | `POST /api/inventory/reserve` *(consumer of order.created)* | `ReserveRequest` → `ReserveResponse` | — | db:`reservations` · cache:`sku-availability` · **consumes `order.created`** |

- **Events touched:** `topic:order.created` — `get_dependents` → producers `[order-service]`, consumers `[inventory-service]`. inventory-service's consumer must accept `reservedQty`.
- **Confidence note:** computed via `get_dependents`/`impact_report` — deterministic, re-runnable. Exact schemas from `get_api`.
