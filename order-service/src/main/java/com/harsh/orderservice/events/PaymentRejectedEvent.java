package com.harsh.orderservice.events;

import java.util.UUID;

/** Published by payment-service when payment fails (e.g. insufficient funds). Triggers compensation. */
public record PaymentRejectedEvent(UUID orderId, String reason) {}
