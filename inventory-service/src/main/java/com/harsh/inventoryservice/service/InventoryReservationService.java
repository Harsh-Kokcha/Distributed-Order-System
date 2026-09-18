package com.harsh.inventoryservice.service;

import com.harsh.inventoryservice.config.RedisLockService;
import com.harsh.inventoryservice.model.InventoryItem;
import com.harsh.inventoryservice.model.ReservationRecord;
import com.harsh.inventoryservice.repository.InventoryRepository;
import com.harsh.inventoryservice.repository.ReservationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the actual reservation/release logic behind the Redis lock, separate
 * from the Kafka listener class. This lets us stress-test the concurrency
 * guarantee directly (see InventoryConcurrencyTest) without needing to spin
 * up a Kafka broker just to prove there's no overselling.
 */
@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final InventoryRepository inventoryRepository;
    private final RedisLockService lockService;
    private final ReservationRecordRepository reservationRecordRepository;

    public InventoryReservationService(InventoryRepository inventoryRepository, RedisLockService lockService,
                                        ReservationRecordRepository reservationRecordRepository) {
        this.inventoryRepository = inventoryRepository;
        this.lockService = lockService;
        this.reservationRecordRepository = reservationRecordRepository;
    }

    public enum Result { RESERVED, INSUFFICIENT_STOCK, UNKNOWN_PRODUCT, LOCK_CONTENTION }

    // If the lock is held by another thread, retry a few times with a short
    // pause instead of giving up immediately. Without this, many concurrent
    // requests for the same product fail with LOCK_CONTENTION even though
    // the lock is only held for a few milliseconds at a time - they just
    // never get a second chance to grab it. (Proven by InventoryConcurrencyTest:
    // without retry, 50 simultaneous requests for 10 units of stock resulted
    // in only 1 successful reservation, not 10 - everyone else bailed out on
    // their first failed attempt instead of waiting their turn.)
    private static final int LOCK_RETRY_ATTEMPTS = 30;
    private static final long LOCK_RETRY_DELAY_MS = 50;

    private String acquireLockWithRetry(String productId) {
        for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
            String token = lockService.tryLock(productId);
            if (token != null) {
                return token;
            }
            try {
                Thread.sleep(LOCK_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    // Kafka only guarantees at-least-once delivery, so onOrderCreated can be
    // invoked more than once for the same orderId. Without a per-order
    // record, a redelivery would reserve the same stock twice. The record
    // is written in the same transaction as the stock update, so a crash
    // between them can't leave the two out of sync; a redelivery that lands
    // after that transaction commits is caught by the check below instead
    // of re-running the reservation.
    @Transactional
    public Result reserve(UUID orderId, String productId, int quantity) {
        Optional<ReservationRecord> existing = reservationRecordRepository.findById(orderId);
        if (existing.isPresent()) {
            log.info("Order {} already processed for product {} (outcome={}), skipping duplicate reservation",
                    orderId, productId, existing.get().getOutcome());
            return Result.valueOf(existing.get().getOutcome());
        }

        String lockToken = acquireLockWithRetry(productId);
        if (lockToken == null) {
            // Not a terminal outcome - deliberately not recorded, so a retry/redelivery tries again.
            return Result.LOCK_CONTENTION;
        }
        try {
            InventoryItem item = inventoryRepository.findById(productId).orElse(null);
            if (item == null) {
                reservationRecordRepository.save(new ReservationRecord(orderId, productId, quantity, Result.UNKNOWN_PRODUCT.name()));
                return Result.UNKNOWN_PRODUCT;
            }
            if (!item.reserve(quantity)) {
                reservationRecordRepository.save(new ReservationRecord(orderId, productId, quantity, Result.INSUFFICIENT_STOCK.name()));
                return Result.INSUFFICIENT_STOCK;
            }
            inventoryRepository.save(item);
            reservationRecordRepository.save(new ReservationRecord(orderId, productId, quantity, Result.RESERVED.name()));
            return Result.RESERVED;
        } finally {
            lockService.unlock(productId, lockToken);
        }
    }

    public void release(String productId, int quantity) {
        String lockToken = acquireLockWithRetry(productId);
        if (lockToken == null) {
            log.error("Could not acquire lock to release {} units of {}", quantity, productId);
            return;
        }
        try {
            inventoryRepository.findById(productId).ifPresent(item -> {
                item.release(quantity);
                inventoryRepository.save(item);
            });
        } finally {
            lockService.unlock(productId, lockToken);
        }
    }
}