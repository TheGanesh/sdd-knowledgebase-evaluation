package com.ganesh.commerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stock level per SKU. Persisted to the {@code inventory} table. */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int availableQuantity;

    protected Inventory() {
        // JPA
    }

    public Inventory(String sku, int availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void decrement(int quantity) {
        this.availableQuantity -= quantity;
    }
}
