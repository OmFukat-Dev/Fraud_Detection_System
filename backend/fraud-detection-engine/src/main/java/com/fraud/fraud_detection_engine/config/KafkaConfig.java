package com.fraud.fraud_detection_engine.config;

import com.fraud.fraud_detection_engine.dto.TransactionRequest;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration — covers both producer and consumer infrastructure.
 *
 * <p>Phase 1 added the producer factory and KafkaTemplate.
 * Phase 2 adds the consumer factory, listener container factory, and topic beans.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${app.kafka.topic.transactions}")
    private String transactionsTopic;

    @Value("${app.kafka.topic.alerts}")
    private String alertsTopic;

    // ──────────────────────────────────────────────────────────────────────────
    //  Producer
    // ──────────────────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Consumer (Phase 2)
    // ──────────────────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, TransactionRequest> consumerFactory() {
        JsonDeserializer<TransactionRequest> deserializer =
                new JsonDeserializer<>(TransactionRequest.class, false);
        deserializer.addTrustedPackages("*");

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionRequest> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3); // process up to 3 partitions concurrently
        return factory;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Topic Declarations (Phase 2)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Declares the raw transaction input topic (4 partitions, replication factor 1
     * for dev; increase for production).
     */
    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(transactionsTopic)
                .partitions(4)
                .replicas(1)
                .build();
    }

    /**
     * Declares the fraud alerts output topic consumed by downstream notification services.
     */
    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name(alertsTopic)
                .partitions(4)
                .replicas(1)
                .build();
    }
}


