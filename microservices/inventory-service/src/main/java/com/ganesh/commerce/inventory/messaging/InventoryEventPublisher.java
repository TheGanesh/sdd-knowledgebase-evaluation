package com.ganesh.commerce.inventory.messaging;

import com.ganesh.commerce.inventory.messaging.event.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes {@code inventory.reserved} events. */
@Component
public class InventoryEventPublisher {

    public static final String INVENTORY_RESERVED_TOPIC = "inventory.reserved";

    private final KafkaTemplate<String, InventoryReservedEvent> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, InventoryReservedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishInventoryReserved(InventoryReservedEvent event) {
        kafkaTemplate.send(INVENTORY_RESERVED_TOPIC, event.sku(), event);
    }
}
