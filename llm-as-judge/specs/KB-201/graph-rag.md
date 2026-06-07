# Feature → Story spec (KB-only) — KB-201

- **KB used:** graph-rag (semantic retrieval + graph expansion)
- **Tool called:** `rag_search("reserve inventory at order creation, add reservedQty to order.created", k=3)`
  - retrieved: `service:inventory-service`, `service:order-service`, `topic:order.created`
  - graphExpanded: (already covered by retrieved set)

## 1. Outcomes
- AC1: reserve stock on order creation. AC2: 409 when unavailable. AC3: `reservedQty` on `order.created`.

## 2. Cross-Service Impact  (the graded section)
- **Affected services:** `order-service`, `inventory-service`

| Service | API operation | Request → Response | Outbound calls | DB / Cache / Kafka |
|---|---|---|---|---|
| `order-service` | `POST /api/orders` | create-order request → order response with reservation; add `409` path | → `inventory-service POST /api/inventory/reserve` | produces `order.created` (add `reservedQty`) |
| `inventory-service` | `POST /api/inventory/reserve` | reserve request → reserve response | — | consumes `order.created`; cache `sku-availability` |

- **Events touched:** `order.created` — the retrieved topic chunk names producer `order-service` and consumer `inventory-service`, so the consumer is flagged for the `reservedQty` change.
- **Confidence note:** found via semantic retrieval; the `order.created` chunk surfaced the consumer. Schemas are paraphrased from chunk text (not exact). Retrieval depends on `k`/phrasing (medium determinism).
