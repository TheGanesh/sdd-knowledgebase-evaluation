package com.ganesh.commerce.inventory.messaging;

import com.ganesh.commerce.inventory.messaging.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code order.created}. The extractor reads this {@code @KafkaListener}
 * to emit a {@code subscribe} channel on the {@code order.created} topic and the
 * dependency edge that makes this service show up in
 * get_dependents("topic:order.created").
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    public static final String ORDER_CREATED_TOPIC = "order.created";

    @KafkaListener(topics = ORDER_CREATED_TOPIC, groupId = "inventory-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        // Reconciliation hook: confirm the reservation referenced by reservedQty.
        log.info("Received order.created for orderId={} sku={} reservedQty={}",
                event.orderId(), event.sku(), event.reservedQty());
    }
}
