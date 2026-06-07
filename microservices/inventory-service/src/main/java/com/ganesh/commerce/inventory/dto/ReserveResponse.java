package com.ganesh.commerce.inventory.dto;

/** Response body for {@code POST /api/inventory/reserve}. */
public record ReserveResponse(
        String reservationId,
        String sku,
        int reservedQuantity,
        boolean available
) {
    public static ReserveResponse unavailable(String sku) {
        return new ReserveResponse(null, sku, 0, false);
    }
}
