package com.harsh.inventoryservice.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
 *  - A watchdog thread renews the TTL (via a compare-and-extend Lua
 *    script, same ownership check as unlock) on a period shorter than the
 *    TTL for as long as the lock is held. Without this, a holder whose
 *    critical section stalls past the TTL (GC pause, slow query) would
 *    silently lose the lock while still believing it holds it, letting a
 *    second thread acquire it and both mutate stock concurrently - the
 *    exact overselling bug this lock exists to prevent.
 *  - This is a single-node lock (fine for this project's scope). A
 *    production system handling real money would use Redlock across
 *    multiple Redis nodes, or push this down into the DB with
 *    SELECT ... FOR UPDATE.
 */
@Component
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);

    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration RENEWAL_INTERVAL = Duration.ofSeconds(2);

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private static final String EXTEND_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "else " +
            "  return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "redis-lock-watchdog");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> renewals = new ConcurrentHashMap<>();

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Returns a lock token if acquired, or null if the product is already locked by someone else. */
    public String tryLock(String productId) {
        String token = UUID.randomUUID().toString();
        String key = LOCK_PREFIX + productId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }
        startRenewal(key, token);
        return token;
    }

    public void unlock(String productId, String token) {
        String key = LOCK_PREFIX + productId;
        stopRenewal(key);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        redisTemplate.execute(script, Collections.singletonList(key), token);
    }

    private void startRenewal(String key, String token) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(EXTEND_SCRIPT, Long.class);
        ScheduledFuture<?> future = watchdog.scheduleAtFixedRate(() -> {
            try {
                Long extended = redisTemplate.execute(script, Collections.singletonList(key),
                        token, String.valueOf(LOCK_TTL.toMillis()));
                if (extended == null || extended == 0) {
                    log.warn("Lock {} no longer owned by this holder - renewal stopped, TTL not extended", key);
                    stopRenewal(key);
                }
            } catch (Exception e) {
                log.warn("Failed to renew lock {}: {}", key, e.getMessage());
            }
        }, RENEWAL_INTERVAL.toMillis(), RENEWAL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        renewals.put(key, future);
    }

    private void stopRenewal(String key) {
        ScheduledFuture<?> future = renewals.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
    }
}
