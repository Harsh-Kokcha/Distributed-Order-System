package com.harsh.inventoryservice.events;

import java.util.UUID;

/** Consumed to release previously-reserved stock when a later saga step fails. */
public record OrderRolledBackEvent(UUID orderId, String productId, int quantity) {}
