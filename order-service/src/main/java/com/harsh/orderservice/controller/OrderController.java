package com.harsh.orderservice.controller;

import com.harsh.orderservice.dto.CreateOrderRequest;
import com.harsh.orderservice.dto.OrderResponse;
import com.harsh.orderservice.events.OrderCreatedEvent;
import com.harsh.orderservice.kafka.OrderEventProducer;
import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.repository.OrderRepository;
import com.harsh.orderservice.service.OrderQueryService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderRepository orderRepository, OrderEventProducer orderEventProducer, OrderQueryService orderQueryService) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // --- Idempotency check ---
        // If a client already sent this exact idempotencyKey before (e.g. it
        // timed out waiting for a response and retried), return the existing
        // order instead of creating a second one. This is what prevents a
        // flaky network from turning into a duplicate charge/order.
        var existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return ResponseEntity.ok(OrderResponse.from(existing.get()));
        }

        Order order = new Order(
                request.idempotencyKey(),
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.amount()
        );

        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // Race condition: two requests with the same idempotency key
            // arrived at nearly the same instant and both passed the check
            // above before either committed. The DB's unique constraint on
            // idempotency_key is the real source of truth here - the
            // in-memory check above is just an optimization to avoid hitting
            // it in the common case.
            Order winner = orderRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> e);
            return ResponseEntity.ok(OrderResponse.from(winner));
        }

        orderEventProducer.publishOrderCreated(new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getProductId(), order.getQuantity(), order.getAmount()
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        // Cache-aside: OrderQueryService checks Redis first, falls back to
        // Postgres on a miss. See CacheConfig for the eviction strategy.
        return ResponseEntity.ok(orderQueryService.getOrder(id));
    }
}
