package com.ganesh.commerce.order.messaging;

import com.ganesh.commerce.order.messaging.event.OrderCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events to Kafka. The extractor reads the topic constant +
 * {@code kafkaTemplate.send(...)} call to emit an AsyncAPI {@code publish}
 * channel on {@code order.created}.
 */
@Component
public class OrderEventPublisher {

    public static final String ORDER_CREATED_TOPIC = "order.created";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(ORDER_CREATED_TOPIC, String.valueOf(event.orderId()), event);
    }
}
