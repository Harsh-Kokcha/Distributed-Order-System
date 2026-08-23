package com.harsh.inventoryservice.kafka;

import com.harsh.inventoryservice.config.KafkaTopics;
import com.harsh.inventoryservice.events.InventoryRejectedEvent;
import com.harsh.inventoryservice.events.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(InventoryReservedEvent event) {
        kafkaTemplate.send(KafkaTopics.INVENTORY_RESERVED_TOPIC, event.orderId().toString(), event);
    }

    public void publishRejected(InventoryRejectedEvent event) {
        kafkaTemplate.send(KafkaTopics.INVENTORY_REJECTED_TOPIC, event.orderId().toString(), event);
    }
}
