package com.ganesh.commerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/inventory/reserve} (called by order-service). */
public record ReserveRequest(
        @NotBlank String sku,
        @Min(1) int quantity,
        Long orderId
) {
}
