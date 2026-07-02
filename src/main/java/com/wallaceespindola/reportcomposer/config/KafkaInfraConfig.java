package com.wallaceespindola.reportcomposer.config;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * The app provisions its own Kafka topics on startup (PRD §3.4/FR-15) via Boot's
 * KafkaAdmin + NewTopic beans — deterministic partition counts, no manual commands.
 */
@Configuration
public class KafkaInfraConfig {

    @Bean
    public NewTopic requestTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().requestTopic())
                .partitions(props.kafka().requestPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic replyTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().replyTopic()).partitions(1).replicas(1).build();
    }

    @Bean
    public KafkaTemplate<String, byte[]> batchKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    @Bean
    public ConsumerFactory<String, byte[]> batchConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
