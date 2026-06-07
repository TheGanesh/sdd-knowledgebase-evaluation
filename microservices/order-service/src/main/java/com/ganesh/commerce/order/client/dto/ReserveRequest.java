package com.ganesh.commerce.order.client.dto;

/** Request payload sent to inventory-service {@code POST /api/inventory/reserve}. */
public record ReserveRequest(String sku, int quantity, Long orderId) {
}
