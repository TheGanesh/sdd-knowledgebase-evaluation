# KB-201: Reserve inventory when an order is created

**Type:** Feature (cross-service, Kafka consumer)

`POST /api/orders` should synchronously reserve stock in inventory-service and reject with
`409 OUT_OF_STOCK` if unavailable. The `order.created` event must also carry `reservedQty`.

## Acceptance Criteria
1. `POST /api/orders` reserves stock before confirming.
2. Out of stock → `409`, order not persisted.
3. `order.created` includes `reservedQty`.
