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
class TransferKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private TransferService transferService;

    @MockitoBean
    private AccountClient accountClient;

    private KafkaMessageListenerContainer<String, String> container;

    private CountDownLatch latch;
    private List<ConsumerRecord<String, String>> received;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() throws Exception {
        latch = new CountDownLatch(2);
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
                new ContainerProperties("transfer-notifications"));
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
    void transfer_shouldSendTwoEvents() throws InterruptedException, java.io.IOException {
        AccountInfoDto mockResult = new AccountInfoDto("Sender", "2000-01-01", new BigDecimal("900.00"));
        when(accountClient.changeBalance(anyString(), any(BigDecimal.class)))
                .thenReturn(mockResult);

        transferService.transfer("sender", "receiver", new BigDecimal("100.00"));

        boolean receivedAll = latch.await(5, TimeUnit.SECONDS);
        assertThat(receivedAll).isTrue();
        assertThat(received).hasSize(2);

        received.sort(Comparator.comparing(ConsumerRecord::key));

        ConsumerRecord<String, String> senderRecord = received.stream()
                .filter(r -> r.key().equals("sender"))
                .findFirst().orElseThrow();
        assertThat(senderRecord.topic()).isEqualTo("transfer-notifications");

        NotificationEvent senderEvent = objectMapper.readValue(senderRecord.value(), NotificationEvent.class);
        assertThat(senderEvent.getLogin()).isEqualTo("sender");
        assertThat(senderEvent.getType()).isEqualTo(NotificationType.TRANSFER_SENT);
        assertThat(senderEvent.getFromLogin()).isEqualTo("sender");
        assertThat(senderEvent.getToLogin()).isEqualTo("receiver");
        assertThat(senderEvent.getAmount()).isEqualTo("100.00");
        assertThat(senderEvent.getEventVersion()).isEqualTo("1.0");
        assertThat(senderEvent.getEventId()).isNotNull();

        ConsumerRecord<String, String> receiverRecord = received.stream()
                .filter(r -> r.key().equals("receiver"))
                .findFirst().orElseThrow();

        NotificationEvent receiverEvent = objectMapper.readValue(receiverRecord.value(), NotificationEvent.class);
        assertThat(receiverEvent.getLogin()).isEqualTo("receiver");
        assertThat(receiverEvent.getType()).isEqualTo(NotificationType.TRANSFER_RECEIVED);
        assertThat(receiverEvent.getFromLogin()).isEqualTo("sender");
        assertThat(receiverEvent.getToLogin()).isEqualTo("receiver");
        assertThat(receiverEvent.getAmount()).isEqualTo("100.00");
        assertThat(receiverEvent.getEventVersion()).isEqualTo("1.0");
        assertThat(receiverEvent.getEventId()).isNotNull();
    }

    @Test
    void transfer_shouldThrowWhenSameUser() {
        assertThatThrownBy(() -> transferService.transfer("user1", "user1", new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Нельзя перевести деньги самому себе");
    }

    @Test
    void transfer_shouldThrowWhenAmountIsZero() {
        assertThatThrownBy(() -> transferService.transfer("user1", "user2", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Сумма перевода должна быть положительной");
    }
}
