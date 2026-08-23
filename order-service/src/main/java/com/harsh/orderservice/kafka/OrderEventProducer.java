package com.harsh.orderservice.kafka;

import com.harsh.orderservice.config.KafkaTopicConfig;
import com.harsh.orderservice.events.OrderCreatedEvent;
import com.harsh.orderservice.events.OrderRolledBackEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        // Key by orderId so all events for the same order land on the same
        // partition and are processed in order.
        kafkaTemplate.send(KafkaTopicConfig.ORDER_CREATED_TOPIC, event.orderId().toString(), event);
    }

    public void publishOrderRolledBack(OrderRolledBackEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.ORDER_ROLLED_BACK_TOPIC, event.orderId().toString(), event);
    }
}
