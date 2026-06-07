# KB-205: Tighten sku-availability cache freshness

**Type:** Feature (single-service — precision test)

The `sku-availability` cache can serve stale stock. Reduce its TTL and evict the SKU on every
reservation so `GET /api/inventory/{sku}` reflects reality sooner. Inventory-service only.

## Acceptance Criteria
1. `sku-availability` TTL reduced; entry evicted on reserve.
2. `GET /api/inventory/{sku}` reflects a just-made reservation within the new TTL.
