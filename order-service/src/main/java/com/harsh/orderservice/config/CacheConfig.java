package com.harsh.orderservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache-aside pattern on GET /orders/{id}: read from Redis first, fall back
 * to Postgres on a miss, populate the cache on the way back. The interesting
 * part isn't the read path - it's cache invalidation: every time
 * OrderEventConsumer updates an order's status (inventory reserved, payment
 * confirmed, rolled back...), it evicts that order's cache entry so a client
 * polling GET /orders/{id} never sees stale status. That's the classic
 * "cache invalidation is the hard part" trade-off worth discussing in an
 * interview - here it's handled by eviction-on-write rather than a short TTL
 * alone, though a 30s TTL is kept as a safety net too (see application.yml).
 */
@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }
}
