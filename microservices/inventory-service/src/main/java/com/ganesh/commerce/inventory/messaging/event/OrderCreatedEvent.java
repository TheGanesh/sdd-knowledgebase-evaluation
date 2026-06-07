package com.ganesh.commerce.inventory.messaging.event;

import java.time.Instant;

/**
 * Consumed from the {@code order.created} topic (produced by order-service).
 * This is inventory-service's copy of the contract; if order-service changes the
 * schema (e.g. COMM-201 adds {@code reservedQty}), this consumer is the impacted party
 * the catalog flags via get_dependents("topic:order.created").
 */
public record OrderCreatedEvent(
        Long orderId,
        Long customerId,
        String sku,
        int quantity,
        int reservedQty,
        Instant occurredAt
) {
}
