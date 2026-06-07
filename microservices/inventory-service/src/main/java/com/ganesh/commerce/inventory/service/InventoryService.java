package com.ganesh.commerce.inventory.service;

import com.ganesh.commerce.inventory.config.CacheConfig;
import com.ganesh.commerce.inventory.domain.Inventory;
import com.ganesh.commerce.inventory.domain.Reservation;
import com.ganesh.commerce.inventory.dto.InventoryResponse;
import com.ganesh.commerce.inventory.dto.ReserveRequest;
import com.ganesh.commerce.inventory.dto.ReserveResponse;
import com.ganesh.commerce.inventory.exception.NotFoundException;
import com.ganesh.commerce.inventory.messaging.InventoryEventPublisher;
import com.ganesh.commerce.inventory.messaging.event.InventoryReservedEvent;
import com.ganesh.commerce.inventory.repository.InventoryRepository;
import com.ganesh.commerce.inventory.repository.ReservationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryEventPublisher eventPublisher;

    public InventoryService(InventoryRepository inventoryRepository,
                            ReservationRepository reservationRepository,
                            InventoryEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Cached read of current stock for a SKU. */
    @Cacheable(value = CacheConfig.SKU_AVAILABILITY_CACHE, key = "#sku")
    @Transactional(readOnly = true)
    public InventoryResponse getAvailability(String sku) {
        return InventoryResponse.from(loadSku(sku));
    }

    /**
     * Reserve stock for an order. Evicts the cached availability for the SKU.
     * Returns {@code available = false} when stock is insufficient (the signal that
     * drives order-service's 409 OUT_OF_STOCK path in COMM-201).
     */
    @CacheEvict(value = CacheConfig.SKU_AVAILABILITY_CACHE, key = "#request.sku()")
    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        Inventory inventory = loadSku(request.sku());
        if (inventory.getAvailableQuantity() < request.quantity()) {
            return ReserveResponse.unavailable(request.sku());
        }

        inventory.decrement(request.quantity());
        inventoryRepository.save(inventory);

        Reservation reservation = reservationRepository.save(
                new Reservation(request.sku(), request.quantity(), request.orderId()));

        eventPublisher.publishInventoryReserved(new InventoryReservedEvent(
                reservation.getId(),
                reservation.getSku(),
                reservation.getQuantity(),
                reservation.getOrderId(),
                Instant.now()));

        return new ReserveResponse(reservation.getId(), request.sku(), request.quantity(), true);
    }

    private Inventory loadSku(String sku) {
        return inventoryRepository.findById(sku)
                .orElseThrow(() -> new NotFoundException("SKU " + sku + " not found"));
    }
}
