package com.harsh.orderservice.events;

import java.util.UUID;

/**
 * Published by order-service when payment failed AFTER inventory was already reserved.
 * inventory-service consumes this and releases the stock it reserved earlier - this is
 * the compensating transaction that makes this a real SAGA instead of just an if/else chain.
 */
public record OrderRolledBackEvent(UUID orderId, String productId, int quantity) {}
