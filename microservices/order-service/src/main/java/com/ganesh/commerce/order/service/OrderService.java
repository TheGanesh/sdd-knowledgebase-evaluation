package com.ganesh.commerce.order.service;

import com.ganesh.commerce.order.client.InventoryClient;
import com.ganesh.commerce.order.client.dto.ReserveRequest;
import com.ganesh.commerce.order.client.dto.ReserveResponse;
import com.ganesh.commerce.order.config.CacheConfig;
import com.ganesh.commerce.order.domain.Order;
import com.ganesh.commerce.order.dto.CreateOrderRequest;
import com.ganesh.commerce.order.exception.BusinessRuleException;
import com.ganesh.commerce.order.exception.NotFoundException;
import com.ganesh.commerce.order.messaging.OrderEventPublisher;
import com.ganesh.commerce.order.messaging.event.OrderCreatedEvent;
import com.ganesh.commerce.order.repository.OrderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Orchestrates order creation across the three integration points the catalog tracks:
 * an outbound Feign call (inventory reservation), a JPA write (orders table), and a
 * Kafka publish (order.created). COMM-201 adds the reservation + OUT_OF_STOCK path.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        InventoryClient inventoryClient,
                        OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // Cache-backed catalog lookup (exercises the "plans" cache region).
        resolvePlanCode(request.sku());

        Order order = new Order(request.customerId(), request.sku(), request.quantity());
        order = orderRepository.save(order);

        // Outbound: synchronously reserve stock in inventory-service.
        ReserveResponse reservation = inventoryClient.reserve(
                new ReserveRequest(request.sku(), request.quantity(), order.getId()));

        if (!reservation.available()) {
            // Rolls back the persisted order (same transaction) and surfaces 409.
            throw new BusinessRuleException(
                    "OUT_OF_STOCK",
                    "SKU " + request.sku() + " is not available in the requested quantity.",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        order.setReservationId(reservation.reservationId());
        order.setStatus(com.ganesh.commerce.order.domain.OrderStatus.CONFIRMED);

        eventPublisher.publishOrderCreated(new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getSku(),
                order.getQuantity(),
                reservation.reservedQuantity(),
                Instant.now()));

        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    /**
     * Stand-in for a slow catalog lookup; cached in the {@code plans} region so the
     * catalog records a cache dependency for this service.
     */
    @Cacheable(CacheConfig.PLANS_CACHE)
    public String resolvePlanCode(String sku) {
        return "PLAN-" + sku.toUpperCase();
    }
}
