package com.harsh.inventoryservice.kafka;

import com.harsh.inventoryservice.config.KafkaTopics;
import com.harsh.inventoryservice.events.InventoryRejectedEvent;
import com.harsh.inventoryservice.events.InventoryReservedEvent;
import com.harsh.inventoryservice.events.OrderCreatedEvent;
import com.harsh.inventoryservice.events.OrderRolledBackEvent;
import com.harsh.inventoryservice.service.InventoryReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final InventoryReservationService reservationService;
    private final InventoryEventProducer eventProducer;

    public InventoryEventConsumer(InventoryReservationService reservationService, InventoryEventProducer eventProducer) {
        this.reservationService = reservationService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED_TOPIC, groupId = "inventory-service-group",
            containerFactory = "orderCreatedListenerFactory")
    public void onOrderCreated(OrderCreatedEvent event) {
        var result = reservationService.reserve(event.productId(), event.quantity());

        switch (result) {
            case RESERVED -> {
                log.info("Reserved {} units of {} for order {}", event.quantity(), event.productId(), event.orderId());
                eventProducer.publishReserved(new InventoryReservedEvent(
                        event.orderId(), event.productId(), event.quantity(), event.customerId(), event.amount()));
            }
            case INSUFFICIENT_STOCK -> {
                log.info("Insufficient stock for product {} (order {})", event.productId(), event.orderId());
                eventProducer.publishRejected(new InventoryRejectedEvent(event.orderId(), "Insufficient stock"));
            }
            case UNKNOWN_PRODUCT -> {
                log.warn("Unknown product {} (order {})", event.productId(), event.orderId());
                eventProducer.publishRejected(new InventoryRejectedEvent(event.orderId(), "Unknown product: " + event.productId()));
            }
            case LOCK_CONTENTION -> {
                // In production, retry with backoff a few times before giving up.
                // Rejecting immediately keeps this demo simple and deterministic.
                log.warn("Lock contention on product {} (order {}), rejecting", event.productId(), event.orderId());
                eventProducer.publishRejected(new InventoryRejectedEvent(event.orderId(), "Product temporarily locked, retry"));
            }
        }
    }

    @KafkaListener(topics = KafkaTopics.ORDER_ROLLED_BACK_TOPIC, groupId = "inventory-service-group",
            containerFactory = "orderRolledBackListenerFactory")
    public void onOrderRolledBack(OrderRolledBackEvent event) {
        reservationService.release(event.productId(), event.quantity());
        log.info("Released {} units of {} back to stock (order {} rolled back)",
                event.quantity(), event.productId(), event.orderId());
    }
}
