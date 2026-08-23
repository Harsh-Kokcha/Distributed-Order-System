package com.harsh.orderservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Client-supplied key. If the same key arrives twice (e.g. client retried
    // after a timeout without knowing if the first request succeeded), we
    // return the existing order instead of creating a duplicate. This is the
    // idempotency guarantee - see OrderController.
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // JPA
    }

    public Order(String idempotencyKey, String customerId, String productId, int quantity, BigDecimal amount) {
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void transitionTo(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    // --- getters ---
    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
