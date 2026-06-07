package com.ganesh.commerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A confirmed stock hold. Persisted to the {@code reservations} table. */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column
    private Long orderId;

    @Column(nullable = false)
    private Instant createdAt;

    protected Reservation() {
        // JPA
    }

    public Reservation(String sku, int quantity, Long orderId) {
        this.id = UUID.randomUUID().toString();
        this.sku = sku;
        this.quantity = quantity;
        this.orderId = orderId;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
