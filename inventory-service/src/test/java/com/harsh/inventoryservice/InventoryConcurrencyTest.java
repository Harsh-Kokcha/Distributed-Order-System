package com.harsh.inventoryservice;

import com.harsh.inventoryservice.model.InventoryItem;
import com.harsh.inventoryservice.repository.InventoryRepository;
import com.harsh.inventoryservice.service.InventoryReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the concurrency guarantee that's the actual point of this project:
 * with 10 units of stock and 50 concurrent "orders" each wanting 1 unit,
 * EXACTLY 10 should succeed and 40 should be correctly rejected as
 * out-of-stock - never more than 10 reserved, no matter how the threads
 * interleave.
 *
 * Requires a real Redis instance running locally, since RedisLockService
 * is not mocked here - the whole point is to test the actual lock under
 * real concurrent load: `docker-compose up -d redis` before running this.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    private static final String PRODUCT_ID = "stress-test-product";
    private static final int TOTAL_STOCK = 10;
    private static final int CONCURRENT_REQUESTS = 50;

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void seedStock() {
        inventoryRepository.deleteById(PRODUCT_ID);
        inventoryRepository.save(new InventoryItem(PRODUCT_ID, TOTAL_STOCK));
    }

    @AfterEach
    void cleanUpLock() {
        // In case a previous failed run left a lock key behind.
        redisTemplate.delete("lock:inventory:" + PRODUCT_ID);
    }

    @Test
    void concurrentReservations_neverExceedAvailableStock() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // All threads block here until the last one is queued, so
                    // they hit reserve() at effectively the same instant -
                    // this is what actually exercises the race condition
                    // instead of just running requests one after another.
                    startLine.await();
                    var result = reservationService.reserve(PRODUCT_ID, 1);
                    if (result == InventoryReservationService.Result.RESERVED) {
                        successCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLine.countDown();
                }
            }));
        }

        long start = System.nanoTime();
        startLine.countDown(); // release all threads at once
        boolean completed = finishLine.await(30, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        executor.shutdown();

        assertTrue(completed, "Not all requests completed within timeout");

        InventoryItem finalState = inventoryRepository.findById(PRODUCT_ID).orElseThrow();

        System.out.printf(
                "Stress test: %d concurrent requests for %d units of stock -> %d reserved, %d rejected, in %d ms%n",
                CONCURRENT_REQUESTS, TOTAL_STOCK, successCount.get(), rejectedCount.get(), elapsedMs
        );

        // The actual assertion that matters: never oversold.
        assertEquals(TOTAL_STOCK, successCount.get(), "Should reserve exactly as many units as were in stock");
        assertEquals(CONCURRENT_REQUESTS - TOTAL_STOCK, rejectedCount.get(), "Remaining requests should be correctly rejected");
        assertEquals(TOTAL_STOCK, finalState.getReservedQuantity(), "Reserved quantity should never exceed total stock");
        assertEquals(0, finalState.available(), "No stock should remain available after exactly matching demand");
    }
}
