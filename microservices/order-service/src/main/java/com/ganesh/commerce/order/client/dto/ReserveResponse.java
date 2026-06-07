package com.ganesh.commerce.order.client.dto;

/** Response payload returned by inventory-service {@code POST /api/inventory/reserve}. */
public record ReserveResponse(String reservationId, String sku, int reservedQuantity, boolean available) {
}
