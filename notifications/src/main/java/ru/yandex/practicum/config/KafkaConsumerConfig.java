package ru.yandex.practicum.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.FixedBackOff;
import ru.yandex.practicum.event.NotificationEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.consumer.bootstrap-servers:${spring.kafka.bootstrap-servers:localhost:9092}}")
    private String bootstrapServers;

    @Value("${kafka.dlt.name:${spring.kafka.consumer.group-id}.dlt}")
    private String dltTopicName;

    @Bean
    public ConsumerFactory<String, NotificationEvent> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "notifications-service");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "ru.yandex.practicum.event");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvent.class);
        configProps.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                List.of(TracingKafkaConsumerInterceptor.class.getName()));

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ProducerFactory<String, NotificationEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put("bootstrap.servers", bootstrapServers);
        configProps.put("key.serializer", StringSerializer.class.getName());
        configProps.put("value.serializer", JsonSerializer.class.getName());
        configProps.put("acks", "all");
        configProps.put("retries", 3);
        configProps.put("enable.idempotence", true);
        configProps.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                List.of(TracingKafkaProducerInterceptor.class.getName()));

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, NotificationEvent> kafkaTemplate(
            ProducerFactory<String, NotificationEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
    
    @Bean
    public ProducerFactory<String, Object> dltProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put("bootstrap.servers", bootstrapServers);
        configProps.put("key.serializer", StringSerializer.class.getName());
        configProps.put("value.serializer", JsonSerializer.class.getName());
        configProps.put("acks", "all");
        configProps.put("retries", 3);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationEvent> consumerFactory,
            ProducerFactory<String, Object> dltProducerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        factory.setCommonErrorHandler(createDefaultErrorHandler(dltProducerFactory));

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltListenerContainerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "notifications-service-dlt");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(configProps);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    private DefaultErrorHandler createDefaultErrorHandler(ProducerFactory<String, Object> dltProducerFactory) {
        DefaultErrorHandler errorHandler = getDefaultErrorHandler(dltProducerFactory);

        errorHandler.addRetryableExceptions(
                org.springframework.dao.DataAccessException.class,
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class
        );
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                com.fasterxml.jackson.databind.JsonMappingException.class,
                java.lang.IllegalArgumentException.class
        );

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retry attempt {} for record from topic {} with offset {}",
                    deliveryAttempt, record.topic(), record.offset());
        });

        return errorHandler;
    }

    private DefaultErrorHandler getDefaultErrorHandler(ProducerFactory<String, Object> dltProducerFactory) {
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(dltProducerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    if (exception instanceof org.springframework.kafka.support.serializer.DeserializationException) {
                        log.error("Message sent to DLT '{}': topic={}, partition={}, offset={}, eventId={}, error={}",
                                dltTopicName,
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                extractEventId(record),
                                exception.getMessage());
                    }

                    return new TopicPartition(dltTopicName, -1);
                });

        FixedBackOff backOff = new FixedBackOff(1000L, 3);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    private String extractEventId(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
        if (record.value() instanceof NotificationEvent event) {
            return event.getEventId() != null ? event.getEventId().toString() : "unknown";
        }
        return "N/A (deserialization failed)";
    }

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;
    private static final long RETENTION_MS = 604_800_000L; 
    
    @Bean
    public NewTopic accountNotificationsTopic() {
        return TopicBuilder.name("account-notifications")
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(RETENTION_MS))
                .build();
    }

    @Bean
    public NewTopic cashNotificationsTopic() {
        return TopicBuilder.name("cash-notifications")
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(RETENTION_MS))
                .build();
    }

    @Bean
    public NewTopic transferNotificationsTopic() {
        return TopicBuilder.name("transfer-notifications")
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(RETENTION_MS))
                .build();
    }

    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(dltTopicName)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(RETENTION_MS))
                .build();
    }

}
