package com.ganesh.commerce.inventory.controller;

import com.ganesh.commerce.inventory.dto.InventoryResponse;
import com.ganesh.commerce.inventory.dto.ReserveRequest;
import com.ganesh.commerce.inventory.dto.ReserveResponse;
import com.ganesh.commerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST surface. {@code POST /api/inventory/reserve} is the operation
 * order-service depends on (catalog edge: order-service consumesApis this op).
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    public InventoryResponse get(@PathVariable String sku) {
        return inventoryService.getAvailability(sku);
    }

    @PostMapping("/reserve")
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request);
    }
}
