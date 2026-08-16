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
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = {"cash-notifications"})
@ActiveProfiles("test")
class CashKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CashService cashService;

    @MockitoBean
    private AccountClient accountClient;

    @Test
    void processCash_shouldSendMessageToKafkaTopic() {
        when(accountClient.changeBalance(anyString(), any()))
                .thenReturn(new AccountInfoDto("Test User", "2000-01-01", new BigDecimal("1100.00")));

        cashService.processCash("user1", new BigDecimal("100"), CashAction.PUT);

        assertThat(kafkaTemplate).isNotNull();
        assertThat(kafkaTemplate.getProducerFactory()).isNotNull();
    }

    @Test
    void processCash_withZeroAmount_shouldThrowException() {
        assertThatThrownBy(() -> cashService.processCash("user1", BigDecimal.ZERO, CashAction.GET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Сумма должна быть положительной");
    }

    @Test
    void processCash_withNegativeAmount_shouldThrowException() {
        assertThatThrownBy(() -> cashService.processCash("user1", new BigDecimal("-10"), CashAction.GET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Сумма должна быть положительной");
    }
}