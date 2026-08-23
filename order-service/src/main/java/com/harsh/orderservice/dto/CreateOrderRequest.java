package com.harsh.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String customerId,
        @NotBlank String productId,
        @Positive int quantity,
        @Positive BigDecimal amount
) {}
