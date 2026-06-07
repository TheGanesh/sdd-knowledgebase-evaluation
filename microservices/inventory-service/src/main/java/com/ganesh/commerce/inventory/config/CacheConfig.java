package com.ganesh.commerce.inventory.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine cache. The {@code sku-availability} region caches stock lookups and is
 * evicted on reservation. The extractor maps it to a catalog Resource of kind {@code cache}.
 */
@Configuration
public class CacheConfig {

    public static final String SKU_AVAILABILITY_CACHE = "sku-availability";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SKU_AVAILABILITY_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(30)));
        return manager;
    }
}
