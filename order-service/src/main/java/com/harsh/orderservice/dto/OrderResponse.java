package com.harsh.orderservice.dto;

import com.harsh.orderservice.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        String customerId,
        String productId,
        int quantity,
        BigDecimal amount,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
