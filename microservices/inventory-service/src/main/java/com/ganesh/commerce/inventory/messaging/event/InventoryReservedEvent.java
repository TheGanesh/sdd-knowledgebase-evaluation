package com.ganesh.commerce.inventory.messaging.event;

import java.time.Instant;

/** Published to the {@code inventory.reserved} topic after a successful reservation. */
public record InventoryReservedEvent(
        String reservationId,
        String sku,
        int quantity,
        Long orderId,
        Instant occurredAt
) {
}
