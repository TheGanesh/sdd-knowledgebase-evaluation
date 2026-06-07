package com.ganesh.commerce.order.messaging.event;

import java.time.Instant;

/**
 * Message published to the {@code order.created} Kafka topic.
 * inventory-service consumes this event. The extractor maps it to an
 * AsyncAPI message schema on the {@code order.created} channel.
 *
 * <p>{@code reservedQty} is the field COMM-201 adds — used here to demonstrate
 * how a Kafka schema change ripples to the downstream consumer.
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
