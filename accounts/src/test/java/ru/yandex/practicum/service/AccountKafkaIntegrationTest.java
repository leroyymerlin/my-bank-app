package ru.yandex.practicum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.event.NotificationEvent;
import ru.yandex.practicum.event.NotificationType;
import ru.yandex.practicum.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccountKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");

    @Autowired
    private AccountService accountService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AccountRepository accountRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private BalanceUpdateService balanceUpdateService;

    private ObjectMapper objectMapper = new ObjectMapper();
    {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    private NotificationEvent waitForMessage(org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer, String expectedKey, int maxAttempts) throws Exception {
        for (int i = 0; i < maxAttempts; i++) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2000));
            for (ConsumerRecord<String, String> record : records) {
                if (record.key().equals(expectedKey)) {
                    return objectMapper.readValue(record.value(), NotificationEvent.class);
                }
            }
        }
        throw new AssertionError("No message found with key=" + expectedKey);
    }

    @Test
    void updateAccount_shouldSendProfileUpdatedEvent() throws Exception {
        Account testAccount = new Account();
        testAccount.setLogin("testuser");
        testAccount.setName("Old Name");
        testAccount.setBirthdate(LocalDate.of(1990, 1, 1));
        testAccount.setBalance(new BigDecimal("1000.00"));
        when(accountRepository.findByLogin("testuser")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> { Account s = inv.getArgument(0); s.setId(1L); return s; });

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "integration");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(props);
        consumer.subscribe(Collections.singletonList("account-notifications"));

        accountService.updateAccount("testuser", "New Name", LocalDate.of(1990, 6, 15));

        NotificationEvent event = waitForMessage(consumer, "testuser", 10);
        assertThat(event.getLogin()).isEqualTo("testuser");
        assertThat(event.getType()).isEqualTo(NotificationType.PROFILE_UPDATED);
        assertThat(event.getMessage()).isEqualTo("Ваши данные профиля были обновлены.");
        assertThat(event.getEventVersion()).isEqualTo("1.0");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
        consumer.close();
    }

    @Test
    void updateBalance_shouldSendBalanceUpdatedEvent() throws Exception {
        Account testAccount = new Account();
        testAccount.setLogin("balanceuser");
        testAccount.setName("Balance User");
        testAccount.setBirthdate(LocalDate.of(1990, 1, 1));
        testAccount.setBalance(new BigDecimal("1000.00"));
        when(accountRepository.findByLogin("balanceuser")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> { Account s = inv.getArgument(0); s.setId(1L); return s; });

        AccountInfoDto mockResult = new AccountInfoDto("Balance User", "1990-01-01", new BigDecimal("1100.00"));
        when(balanceUpdateService.doUpdateBalance("balanceuser", new BigDecimal("100.00"))).thenReturn(mockResult);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-balance");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(props);
        consumer.subscribe(Collections.singletonList("account-notifications"));

        accountService.updateBalance("balanceuser", new BigDecimal("100.00"));

        NotificationEvent event = waitForMessage(consumer, "balanceuser", 10);
        assertThat(event.getLogin()).isEqualTo("balanceuser");
        assertThat(event.getType()).isEqualTo(NotificationType.BALANCE_UPDATED);
        assertThat(event.getMessage()).contains("100.00");
        assertThat(event.getEventVersion()).isEqualTo("1.0");
        assertThat(event.getEventId()).isNotNull();
        consumer.close();
    }
}
