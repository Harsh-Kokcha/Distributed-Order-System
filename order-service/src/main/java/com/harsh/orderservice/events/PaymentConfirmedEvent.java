package com.harsh.orderservice.events;

import java.util.UUID;

/** Published by payment-service when the customer's funds were successfully reserved. */
public record PaymentConfirmedEvent(UUID orderId) {}
