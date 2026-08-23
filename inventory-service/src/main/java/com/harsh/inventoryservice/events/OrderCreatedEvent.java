package com.harsh.inventoryservice.events;

import java.math.BigDecimal;
import java.util.UUID;

/** Local copy of order-service's event. Each service owns its own event
 *  classes rather than sharing a library - keeps services independently
 *  deployable, at the cost of some duplication. */
public record OrderCreatedEvent(
        UUID orderId,
        String customerId,
        String productId,
        int quantity,
        BigDecimal amount
) {}
