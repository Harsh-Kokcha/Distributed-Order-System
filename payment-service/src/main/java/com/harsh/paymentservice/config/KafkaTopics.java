package com.harsh.paymentservice.config;

public final class KafkaTopics {
    public static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
    public static final String PAYMENT_CONFIRMED_TOPIC = "payment-confirmed";
    public static final String PAYMENT_REJECTED_TOPIC = "payment-rejected";

    private KafkaTopics() {}
}
