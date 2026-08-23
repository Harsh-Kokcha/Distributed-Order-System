package com.harsh.orderservice.model;

/**
 * SAGA state machine for an order.
 *
 * Happy path:   PENDING -> INVENTORY_RESERVED -> PAYMENT_CONFIRMED -> COMPLETED
 * Failure path: PENDING -> INVENTORY_REJECTED
 *               PENDING -> INVENTORY_RESERVED -> PAYMENT_REJECTED -> ROLLING_BACK -> ROLLED_BACK
 *
 * The ROLLING_BACK step matters: if inventory was reserved but payment fails,
 * we can't just mark it failed - we have to tell inventory-service to release
 * the stock it already reserved. That compensating action is the actual SAGA pattern.
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    INVENTORY_REJECTED,
    PAYMENT_CONFIRMED,
    PAYMENT_REJECTED,
    ROLLING_BACK,
    ROLLED_BACK,
    COMPLETED
}
