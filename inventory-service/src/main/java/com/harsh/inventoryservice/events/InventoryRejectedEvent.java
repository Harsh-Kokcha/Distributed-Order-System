package com.harsh.inventoryservice.events;

import java.util.UUID;

public record InventoryRejectedEvent(UUID orderId, String reason) {}
