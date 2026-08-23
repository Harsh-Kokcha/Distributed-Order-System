package com.harsh.paymentservice.config;

import com.harsh.paymentservice.events.InventoryReservedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.harsh.paymentservice.events");
        return props;
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factoryFor(Class<T> type, DefaultErrorHandler errorHandler) {
        Map<String, Object> props = baseConsumerProps();
        ConsumerFactory<String, T> cf = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(type, false));
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReservedEvent> inventoryReservedListenerFactory(DefaultErrorHandler errorHandler) {
        return factoryFor(InventoryReservedEvent.class, errorHandler);
    }
}
