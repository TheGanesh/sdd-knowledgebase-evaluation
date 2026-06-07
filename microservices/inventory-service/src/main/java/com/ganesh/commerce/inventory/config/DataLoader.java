package com.ganesh.commerce.inventory.config;

import com.ganesh.commerce.inventory.domain.Inventory;
import com.ganesh.commerce.inventory.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Seeds a couple of SKUs so GET/reserve return data in local runs. */
@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner seedInventory(InventoryRepository repository) {
        return args -> {
            repository.save(new Inventory("IPHONE-15-PRO", 25));
            repository.save(new Inventory("NOKIA-G22", 0)); // out of stock -> drives OUT_OF_STOCK demo
        };
    }
}
