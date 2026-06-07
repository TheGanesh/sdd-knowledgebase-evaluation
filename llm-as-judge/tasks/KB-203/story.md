# KB-203: Add customerTier to the order.created event

**Type:** Feature (Kafka schema change — consumer impact only)

Add a `customerTier` field to the `order.created` event so downstream consumers can prioritise.
No REST API request/response changes.

## Acceptance Criteria
1. `order.created` includes `customerTier`.
2. Consumers continue to deserialize successfully (additive change).
