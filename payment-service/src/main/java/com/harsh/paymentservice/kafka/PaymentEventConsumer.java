package com.harsh.paymentservice.kafka;

import com.harsh.paymentservice.config.KafkaTopics;
import com.harsh.paymentservice.events.InventoryReservedEvent;
import com.harsh.paymentservice.events.PaymentConfirmedEvent;
import com.harsh.paymentservice.events.PaymentRejectedEvent;
import com.harsh.paymentservice.model.Account;
import com.harsh.paymentservice.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final AccountRepository accountRepository;
    private final PaymentEventProducer eventProducer;

    public PaymentEventConsumer(AccountRepository accountRepository, PaymentEventProducer eventProducer) {
        this.accountRepository = accountRepository;
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
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Optional<Account> accountOpt = accountRepository.findById(event.customerId());
                if (accountOpt.isEmpty()) {
                    eventProducer.publishRejected(new PaymentRejectedEvent(event.orderId(), "Unknown customer: " + event.customerId()));
                    return;
                }

                Account account = accountOpt.get();
                boolean debited = account.debit(event.amount());
                if (!debited) {
                    log.info("Insufficient funds for customer {} (order {}): balance {}, needed {}",
                            event.customerId(), event.orderId(), account.getBalance(), event.amount());
                    eventProducer.publishRejected(new PaymentRejectedEvent(event.orderId(), "Insufficient funds"));
                    return;
                }

                accountRepository.save(account);
                log.info("Charged {} to customer {} for order {}", event.amount(), event.customerId(), event.orderId());
                eventProducer.publishConfirmed(new PaymentConfirmedEvent(event.orderId()));
                return;

            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on customer {} (attempt {}/{}), retrying", event.customerId(), attempt, MAX_RETRIES);
                if (attempt == MAX_RETRIES) {
                    eventProducer.publishRejected(new PaymentRejectedEvent(event.orderId(), "Too many concurrent payment attempts, please retry"));
                    return;
                }
            }
        }
    }
}
