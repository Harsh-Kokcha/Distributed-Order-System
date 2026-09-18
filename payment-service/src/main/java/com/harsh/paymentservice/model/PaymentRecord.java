package com.harsh.paymentservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One row per orderId, written in the same transaction as the account
 * debit. Kafka's at-least-once delivery means onInventoryReserved can be
 * invoked more than once for the same order (retry after a transient
 * failure, redelivery after a rebalance, etc). Without this record, a
 * redelivered message would debit the customer's account a second time -
 * this table is what makes the listener idempotent: check for an existing
 * record before charging, and only charge if one doesn't exist yet.
 */
@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOutcome outcome;

    @Column(nullable = false)
    private String reason;

    protected PaymentRecord() {
        // JPA
    }

    public PaymentRecord(UUID orderId, PaymentOutcome outcome, String reason) {
        this.orderId = orderId;
        this.outcome = outcome;
        this.reason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public PaymentOutcome getOutcome() { return outcome; }
    public String getReason() { return reason; }
}
