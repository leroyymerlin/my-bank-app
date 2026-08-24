package ru.yandex.practicum.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;
    private static final long RETENTION_MS = 604_800_000L;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public NewTopic transferNotificationsTopic() {
        return TopicBuilder.name("transfer-notifications")
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG, String.valueOf(RETENTION_MS))
                .build();
    }

    @Bean
    public NewTopic transferNotificationsDlt() {
        return TopicBuilder.name("transfer-notifications.dlt")
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG, String.valueOf(RETENTION_MS))
                .build();
    }
}
