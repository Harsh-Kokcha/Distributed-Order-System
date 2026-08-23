package com.harsh.orderservice.kafka;

import com.harsh.orderservice.config.KafkaTopicConfig;
import com.harsh.orderservice.events.*;
import com.harsh.orderservice.model.Order;
import com.harsh.orderservice.model.OrderStatus;
import com.harsh.orderservice.repository.OrderRepository;
import com.harsh.orderservice.service.OrderQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * This class IS the SAGA orchestrator. It reacts to what inventory-service and
 * payment-service report back, and decides what happens next - including
 * triggering the compensating rollback if payment fails after inventory
 * already succeeded. This decision logic is the actual "hard part" of the
 * project - everything else is plumbing around it.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final CacheManager cacheManager;

    public OrderEventConsumer(OrderRepository orderRepository, OrderEventProducer orderEventProducer, CacheManager cacheManager) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
        this.cacheManager = cacheManager;
    }

    /**
     * Every status transition invalidates the Redis cache entry for this
     * order. Without this, a client polling GET /orders/{id} right after
     * placing an order could keep seeing a stale "PENDING" for up to the
     * full 30s TTL even after the order actually completed.
     */
    private void evictCache(java.util.UUID orderId) {
        var cache = cacheManager.getCache(OrderQueryService.ORDERS_CACHE);
        if (cache != null) {
            cache.evict(orderId);
        }
    }

    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, groupId = "order-service-group", containerFactory = "inventoryReservedListenerFactory")
    public void onInventoryReserved(InventoryReservedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            log.info("Inventory reserved for order {}", order.getId());
            order.transitionTo(OrderStatus.INVENTORY_RESERVED);
            orderRepository.save(order);
            evictCache(order.getId());
            // Next step in the saga: payment-service is independently listening
            // to inventory-events too, so it will pick this same event up and
            // attempt payment. order-service doesn't need to explicitly call it.
        });
    }

    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_REJECTED_TOPIC, groupId = "order-service-group", containerFactory = "inventoryRejectedListenerFactory")
    public void onInventoryRejected(InventoryRejectedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            log.warn("Inventory rejected for order {}: {}", order.getId(), event.reason());
            order.transitionTo(OrderStatus.INVENTORY_REJECTED);
            orderRepository.save(order);
            evictCache(order.getId());
            // Nothing to compensate - inventory never succeeded, so there's
            // nothing to roll back. The saga ends here.
        });
    }

    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_CONFIRMED_TOPIC, groupId = "order-service-group", containerFactory = "paymentConfirmedListenerFactory")
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            log.info("Payment confirmed for order {}", order.getId());
            order.transitionTo(OrderStatus.COMPLETED);
            orderRepository.save(order);
            evictCache(order.getId());
        });
    }

    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_REJECTED_TOPIC, groupId = "order-service-group", containerFactory = "paymentRejectedListenerFactory")
    public void onPaymentRejected(PaymentRejectedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            log.warn("Payment rejected for order {}: {}. Triggering compensation.", order.getId(), event.reason());
            order.transitionTo(OrderStatus.ROLLING_BACK);
            orderRepository.save(order);
            evictCache(order.getId());

            // THIS is the compensating transaction: inventory was already
            // reserved (we wouldn't be here otherwise), so we have to tell
            // inventory-service to give that stock back.
            orderEventProducer.publishOrderRolledBack(
                    new OrderRolledBackEvent(order.getId(), order.getProductId(), order.getQuantity())
            );
            order.transitionTo(OrderStatus.ROLLED_BACK);
            orderRepository.save(order);
            evictCache(order.getId());
        });
    }
}
