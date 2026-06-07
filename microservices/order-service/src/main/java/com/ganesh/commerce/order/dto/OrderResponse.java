package com.ganesh.commerce.order.dto;

import com.ganesh.commerce.order.domain.Order;

import java.time.Instant;

/**
 * Response body for {@code POST /api/orders} and {@code GET /api/orders/{id}}.
 * {@code reservationId} is populated once inventory-service confirms the reservation.
 */
public record OrderResponse(
        Long id,
        Long customerId,
        String sku,
        int quantity,
        String status,
        String reservationId,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getSku(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getReservationId(),
                order.getCreatedAt()
        );
    }
}
