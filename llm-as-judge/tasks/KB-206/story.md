# KB-206: Confirm orders when inventory.reserved arrives

**Type:** Feature (new cross-service event subscription)

order-service should consume the `inventory.reserved` event and move the matching order to
`CONFIRMED` asynchronously (in addition to the synchronous path).

## Acceptance Criteria
1. order-service subscribes to `inventory.reserved`.
2. On the event, the referenced order is set to `CONFIRMED` if not already.
