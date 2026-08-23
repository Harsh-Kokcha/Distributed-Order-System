package com.harsh.orderservice.events;

import java.math.BigDecimal;
import java.util.UUID;

/** Published by order-service after an order is created. Consumed by inventory-service. */
public record OrderCreatedEvent(
        UUID orderId,
        String customerId,
        String productId,
        int quantity,
        BigDecimal amount
) {}
