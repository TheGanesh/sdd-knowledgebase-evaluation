# KB-204: inventory reserve returns availableQty

**Type:** Feature (provider change → caller impact)

`POST /api/inventory/reserve` should add `availableQty` (remaining stock) to its response so the
caller can show "only N left". The caller must read and surface it.

## Acceptance Criteria
1. `reserve` response includes `availableQty`.
2. The calling service reads `availableQty` from the reservation response.
