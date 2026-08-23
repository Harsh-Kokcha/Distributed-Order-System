package com.harsh.orderservice.events;

import java.math.BigDecimal;
import java.util.UUID;

/** Carries customerId and amount forward from OrderCreatedEvent so payment-service
 *  (which never sees the original order-created event) has what it needs to charge. */
public record InventoryReservedEvent(UUID orderId, String productId, int quantity, String customerId, BigDecimal amount) {}
