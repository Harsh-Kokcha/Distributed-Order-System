package com.harsh.orderservice.events;

import java.util.UUID;

/** Published by inventory-service when stock reservation fails (e.g. insufficient stock). */
public record InventoryRejectedEvent(UUID orderId, String reason) {}
