# Service Catalog — markdown view

> Generated from the structured catalog (`catalog-info.yaml` per service). Do not edit by hand — re-run `/build-knowledge-base`.

| Service | System / Domain | Inbound APIs | Produces | Consumes | Calls |
|---|---|---|---|---|---|
| [inventory-service](inventory-service.md) | commerce / fulfillment | GET /api/inventory/{sku}, POST /api/inventory/reserve | inventory.reserved | order.created | — |
| [order-service](order-service.md) | commerce / ordering | POST /api/orders, GET /api/orders/{id} | order.created | — | inventory-service |

*For machine consumption (impact analysis), query the Service Catalog MCP server instead of parsing this file — see `../../README.md`.*
