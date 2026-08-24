package ru.yandex.practicum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.event.NotificationEvent;
import ru.yandex.practicum.event.NotificationType;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CashKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private CashService cashService;

    @MockitoBean
    private AccountClient accountClient;

    private KafkaMessageListenerContainer<String, String> container;

    private CountDownLatch latch;
    private List<ConsumerRecord<String, String>> received;

    private ObjectMapper objectMapper = new ObjectMapper();
    {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() throws Exception {
        latch = new CountDownLatch(1);
        received = new CopyOnWriteArrayList<>();

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(consumerProps);

        Thread.sleep(1000);

        container = new KafkaMessageListenerContainer<>(consumerFactory,
                new ContainerProperties("cash-notifications"));
        container.setupMessageListener((org.springframework.kafka.listener.MessageListener<String, String>) record -> {
            received.add(record);
            latch.countDown();
        });
        container.start();

        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void processCash_PUT_shouldSendCashEvent() throws InterruptedException, java.io.IOException {
        AccountInfoDto mockResult = new AccountInfoDto("Test User", "2000-01-01", new BigDecimal("1100.00"));
        when(accountClient.changeBalance(anyString(), any(BigDecimal.class)))
                .thenReturn(mockResult);

        cashService.processCash("user1", new BigDecimal("100"), CashAction.PUT);

        boolean receivedMessage = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedMessage).isTrue();
        assertThat(received).hasSize(1);

        ConsumerRecord<String, String> record = received.get(0);
        assertThat(record.key()).isEqualTo("user2");
        assertThat(record.topic()).isEqualTo("cash-notifications");

        NotificationEvent event = objectMapper.readValue(record.value(), NotificationEvent.class);
        assertThat(event.getLogin()).isEqualTo("user2");
        assertThat(event.getEventVersion()).isEqualTo("1.0");
        assertThat(event.getEventId()).isNotNull();
    }

    @Test
    void processCash_GET_shouldSendCashEvent() throws InterruptedException, java.io.IOException {
        AccountInfoDto mockResult = new AccountInfoDto("Test User", "2000-01-01", new BigDecimal("900.00"));
        when(accountClient.changeBalance(anyString(), any(BigDecimal.class)))
                .thenReturn(mockResult);

        cashService.processCash("user2", new BigDecimal("50"), CashAction.GET);

        boolean receivedMessage = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedMessage).isTrue();
        assertThat(received).hasSize(1);

        ConsumerRecord<String, String> record = received.get(0);
        assertThat(record.key()).isEqualTo("user2");
        assertThat(record.topic()).isEqualTo("cash-notifications");

        NotificationEvent event = objectMapper.readValue(record.value(), NotificationEvent.class);
        assertThat(event.getLogin()).isEqualTo("user2");
        assertThat(event.getType()).isEqualTo(NotificationType.GET);
        assertThat(event.getMessage()).contains("50");
        assertThat(event.getEventVersion()).isEqualTo("1.0");
        assertThat(event.getEventId()).isNotNull();
    }

    @Test
    void processCash_withZeroAmount_shouldThrowException() {
        assertThatThrownBy(() -> cashService.processCash("user1", BigDecimal.ZERO, CashAction.GET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
    }

    @Test
    void processCash_withNegativeAmount_shouldThrowException() {
        assertThatThrownBy(() -> cashService.processCash("user1", new BigDecimal("-10"), CashAction.GET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
    }
}
