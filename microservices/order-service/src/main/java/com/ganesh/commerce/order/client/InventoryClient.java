package com.ganesh.commerce.order.client;

import com.ganesh.commerce.order.client.dto.ReserveRequest;
import com.ganesh.commerce.order.client.dto.ReserveResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Outbound REST dependency on inventory-service.
 *
 * <p>The extractor reads this {@code @FeignClient} (target service name + the
 * {@code @PostMapping} path) and emits a {@code consumesApis} edge:
 * order-service -> api:inventory-service/POST /api/inventory/reserve.
 */
@FeignClient(name = "inventory-service", path = "/api/inventory", url = "${clients.inventory-service.url}")
public interface InventoryClient {

    @PostMapping("/reserve")
    ReserveResponse reserve(@RequestBody ReserveRequest request);
}
