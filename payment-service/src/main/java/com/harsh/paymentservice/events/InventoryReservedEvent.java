package com.harsh.paymentservice.events;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryReservedEvent(UUID orderId, String productId, int quantity, String customerId, BigDecimal amount) {}
