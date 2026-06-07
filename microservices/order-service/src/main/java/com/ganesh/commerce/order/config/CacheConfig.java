package com.ganesh.commerce.order.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine cache. The {@code plans} region is read by {@link com.ganesh.commerce.order.service.OrderService}.
 * The extractor maps {@code @Cacheable} regions to catalog Resources of kind {@code cache}.
 */
@Configuration
public class CacheConfig {

    public static final String PLANS_CACHE = "plans";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(PLANS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10)));
        return manager;
    }
}
