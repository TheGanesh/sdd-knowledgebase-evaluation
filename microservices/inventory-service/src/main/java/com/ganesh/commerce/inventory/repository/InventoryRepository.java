package com.ganesh.commerce.inventory.repository;

import com.ganesh.commerce.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, String> {
}
