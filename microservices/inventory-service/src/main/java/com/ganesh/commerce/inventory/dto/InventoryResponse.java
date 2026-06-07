package com.ganesh.commerce.inventory.dto;

import com.ganesh.commerce.inventory.domain.Inventory;

/** Response body for {@code GET /api/inventory/{sku}}. */
public record InventoryResponse(String sku, int availableQuantity) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(inventory.getSku(), inventory.getAvailableQuantity());
    }
}
