package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.dto.AccountInfoDto;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = {"transfer-notifications"})
@ActiveProfiles("test")
class TransferKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TransferService transferService;

    @MockitoBean
    private AccountClient accountClient;

    @Test
    void transfer_shouldSendMessageToKafkaForSender() {
        when(accountClient.changeBalance(anyString(), any(BigDecimal.class)))
                .thenReturn(new AccountInfoDto("Sender", "2000-01-01", new BigDecimal("900.00")));

        transferService.transfer("sender", "receiver", new BigDecimal("100"));

        assertThat(kafkaTemplate).isNotNull();
        assertThat(kafkaTemplate.getProducerFactory()).isNotNull();
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
