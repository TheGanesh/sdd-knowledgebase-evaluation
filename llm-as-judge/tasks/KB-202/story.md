# KB-202: Cap order quantity at 10

**Type:** Feature (single-service — precision test)

`POST /api/orders` should reject any request where `quantity > 10` with `422 QUANTITY_TOO_HIGH`.
Pure validation in order-service.

## Acceptance Criteria
1. `quantity > 10` → `422 QUANTITY_TOO_HIGH`, nothing persisted.
2. `quantity <= 10` still succeeds unchanged.
