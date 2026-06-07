# Feature → Story spec (KB-only) — KB-201

- **KB used:** markdown (prose pages read in context)
- **Read:** `kb/order-service.md`, `kb/inventory-service.md`

## 1. Outcomes
- AC1: reserve stock on order creation. AC2: 409 when unavailable. AC3: `reservedQty` on `order.created`.

## 2. Cross-Service Impact  (the graded section)
- **Affected services:** `order-service`, and `inventory-service` (it provides the reserve endpoint order calls)

| Service | API operation | Request → Response | Outbound calls | DB / Cache / Kafka |
|---|---|---|---|---|
| `order-service` | `POST /api/orders` | create order, return reservation; reject when out of stock | calls inventory reserve (per the page's "Outbound calls") | produces `order.created` (add the reserved quantity) |
| `inventory-service` | `POST /api/inventory/reserve` | reserve stock | — | — |

- **Events touched:** `order.created` is produced by order-service. *inventory-service's page also says it consumes `order.created`, so it may need to handle the new field — please confirm.*
- **Confidence note:** inferred by reading the two pages. The inventory-side consumer change is **hedged** (prose doesn't make it a required step), and request/response are described in words, not exact schemas.
