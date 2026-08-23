package com.harsh.paymentservice.events;

import java.util.UUID;

public record PaymentConfirmedEvent(UUID orderId) {}
