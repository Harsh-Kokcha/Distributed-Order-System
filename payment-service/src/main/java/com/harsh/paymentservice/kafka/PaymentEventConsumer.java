package com.harsh.paymentservice.kafka;

import com.harsh.paymentservice.config.KafkaTopics;
import com.harsh.paymentservice.events.InventoryReservedEvent;
import com.harsh.paymentservice.events.PaymentConfirmedEvent;
import com.harsh.paymentservice.events.PaymentRejectedEvent;
import com.harsh.paymentservice.model.PaymentOutcome;
import com.harsh.paymentservice.model.PaymentRecord;
import com.harsh.paymentservice.service.PaymentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final PaymentProcessingService paymentProcessingService;
    private final PaymentEventProducer eventProducer;

    public PaymentEventConsumer(PaymentProcessingService paymentProcessingService, PaymentEventProducer eventProducer) {
        this.paymentProcessingService = paymentProcessingService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED_TOPIC, groupId = "payment-service-group",
            containerFactory = "inventoryReservedListenerFactory")
    public void onInventoryReserved(InventoryReservedEvent event) {
        // Unlike inventory-service (Redis lock), here we lean on JPA
        // optimistic locking (@Version on Account) plus a small retry loop.
        // Two valid approaches to the same "concurrent writers to one row"
        // problem - worth contrasting in an interview: Redis lock blocks
        // upfront, optimistic locking lets both proceed and makes the loser
        // retry. Optimistic locking wins when conflicts are rare, which is
        // the case here (one customer rarely has two orders debiting at once).

        // Kafka only guarantees at-least-once delivery, so this listener can
        // run more than once for the same orderId (retry after a transient
        // error, consumer-group rebalance, etc). Check for a prior result
        // first and replay it instead of charging the account again.
        var existing = paymentProcessingService.findExisting(event.orderId());
        if (existing.isPresent()) {
            log.info("Order {} already processed (outcome={}), skipping re-charge and replaying result",
                    event.orderId(), existing.get().getOutcome());
            publish(event.orderId(), existing.get());
            return;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                PaymentRecord record = paymentProcessingService.attemptCharge(event.orderId(), event.customerId(), event.amount());
                if (record.getOutcome() == PaymentOutcome.REJECTED) {
                    log.info("Payment rejected for customer {} (order {}): {}",
                            event.customerId(), event.orderId(), record.getReason());
                } else {
                    log.info("Charged {} to customer {} for order {}", event.amount(), event.customerId(), event.orderId());
                }
                publish(event.orderId(), record);
                return;

            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on customer {} (attempt {}/{}), retrying", event.customerId(), attempt, MAX_RETRIES);
                if (attempt == MAX_RETRIES) {
                    PaymentRecord rejected = paymentProcessingService.recordRejection(
                            event.orderId(), "Too many concurrent payment attempts, please retry");
                    publish(event.orderId(), rejected);
                    return;
                }
            }
        }
    }

    private void publish(java.util.UUID orderId, PaymentRecord record) {
        if (record.getOutcome() == PaymentOutcome.CONFIRMED) {
            eventProducer.publishConfirmed(new PaymentConfirmedEvent(orderId));
        } else {
            eventProducer.publishRejected(new PaymentRejectedEvent(orderId, record.getReason()));
        }
    }
}
