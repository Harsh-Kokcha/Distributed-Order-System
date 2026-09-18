package com.harsh.inventoryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One row per orderId that reached a terminal reservation outcome
 * (RESERVED, INSUFFICIENT_STOCK or UNKNOWN_PRODUCT), written in the same
 * transaction as the stock update. Kafka's at-least-once delivery means
 * OrderCreatedEvent can be redelivered for an order that was already
 * reserved - without this record, the redelivery would reserve the same
 * stock a second time. LOCK_CONTENTION is deliberately not recorded here:
 * it isn't a terminal outcome, so a redelivered/retried attempt should
 * just try to acquire the lock again.
 */
@Entity
@Table(name = "reservation_records")
public class ReservationRecord {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    /** Name of the terminal InventoryReservationService.Result this order resolved to. */
    @Column(nullable = false)
    private String outcome;

    protected ReservationRecord() {
        // JPA
    }

    public ReservationRecord(UUID orderId, String productId, int quantity, String outcome) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.outcome = outcome;
    }

    public UUID getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public String getOutcome() { return outcome; }
}
