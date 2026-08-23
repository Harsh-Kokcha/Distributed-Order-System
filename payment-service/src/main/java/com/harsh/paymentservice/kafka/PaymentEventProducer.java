package com.harsh.paymentservice.kafka;

import com.harsh.paymentservice.config.KafkaTopics;
import com.harsh.paymentservice.events.PaymentConfirmedEvent;
import com.harsh.paymentservice.events.PaymentRejectedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishConfirmed(PaymentConfirmedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_CONFIRMED_TOPIC, event.orderId().toString(), event);
    }

    public void publishRejected(PaymentRejectedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_REJECTED_TOPIC, event.orderId().toString(), event);
    }
}
