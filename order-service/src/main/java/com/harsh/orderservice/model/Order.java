package com.harsh.orderservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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

    // Only the transitions the saga actually performs (see OrderStatus) are
    // legal. This is what makes transitionTo a real state machine instead
    // of a bare setter: it rejects a transition that shouldn't be possible
    // (e.g. COMPLETED -> ROLLING_BACK), and - just as important for a
    // system fed by at-least-once Kafka delivery - it's a no-op when the
    // requested status is already the current one, so a redelivered event
    // for a step that already ran doesn't fail or re-run side effects.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.INVENTORY_RESERVED, OrderStatus.INVENTORY_REJECTED),
            OrderStatus.INVENTORY_RESERVED, Set.of(OrderStatus.COMPLETED, OrderStatus.ROLLING_BACK),
            OrderStatus.ROLLING_BACK, Set.of(OrderStatus.ROLLED_BACK)
    );

    public void transitionTo(OrderStatus newStatus) {
        if (newStatus == this.status) {
            return;
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(this.status, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    "Illegal order transition for " + id + ": " + this.status + " -> " + newStatus);
        }
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
