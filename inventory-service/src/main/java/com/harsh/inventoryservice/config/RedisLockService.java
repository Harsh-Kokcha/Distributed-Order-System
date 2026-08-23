package com.harsh.inventoryservice.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * A minimal Redis-backed distributed lock.
 *
 * Why this exists: two orders for the SAME product can arrive on two
 * different service instances (or just two concurrent threads) at almost
 * the same moment. Without coordination, both could read "5 in stock",
 * both decide there's enough, and both reserve - overselling the item.
 * A DB-level unique/optimistic constraint alone doesn't fully solve this
 * for multi-step reservation logic, so we take a lock on the productId
 * before touching its row.
 *
 * Correctness details that matter (and are worth being able to explain
 * in an interview):
 *  - SET key value NX PX ttl is atomic: the lock is only acquired if it
 *    doesn't already exist, with a TTL so a crashed holder doesn't lock
 *    the product forever.
 *  - Unlocking uses a Lua script that checks the lock's value before
 *    deleting it, so service A can never accidentally release a lock
 *    that service B acquired after A's lock expired (the classic "unlock
 *    someone else's lock" bug with naive DEL).
 *  - This is a single-node lock (fine for this project's scope). A
 *    production system handling real money would use Redlock across
 *    multiple Redis nodes, or push this down into the DB with
 *    SELECT ... FOR UPDATE.
 */
@Component
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Returns a lock token if acquired, or null if the product is already locked by someone else. */
    public String tryLock(String productId) {
        String token = UUID.randomUUID().toString();
        String key = LOCK_PREFIX + productId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void unlock(String productId, String token) {
        String key = LOCK_PREFIX + productId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        redisTemplate.execute(script, Collections.singletonList(key), token);
    }
}
