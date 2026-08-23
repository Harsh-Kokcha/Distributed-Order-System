package com.harsh.inventoryservice.config;

public final class KafkaTopics {
    public static final String ORDER_CREATED_TOPIC = "order-created";
    public static final String ORDER_ROLLED_BACK_TOPIC = "order-rolled-back";
    public static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
    public static final String INVENTORY_REJECTED_TOPIC = "inventory-rejected";

    private KafkaTopics() {}
}
