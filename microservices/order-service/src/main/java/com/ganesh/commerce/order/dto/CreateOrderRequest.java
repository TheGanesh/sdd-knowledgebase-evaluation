package com.ganesh.commerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/orders}. springdoc renders this into the
 * OpenAPI request schema that the catalog harvests.
 */
public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotBlank String sku,
        @Min(1) int quantity
) {
}
