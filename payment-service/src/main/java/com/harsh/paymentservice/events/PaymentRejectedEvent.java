package com.harsh.paymentservice.events;

import java.util.UUID;

public record PaymentRejectedEvent(UUID orderId, String reason) {}
