package ru.yandex.practicum.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.event.NotificationEvent;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private CashService cashService;

    @BeforeEach
    void setUp() {
        // Inject meterRegistry into cashService
        cashService = new CashService(accountClient, kafkaTemplate, meterRegistry);
        meterRegistry.clear();
    }

    private static final String TEST_LOGIN = "testuser";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal TEST_BALANCE = new BigDecimal("1000.00");

    @Test
    void processCash_shouldIncreaseBalance_andSendToKafka_onPut() {
        BigDecimal delta = new BigDecimal("500.00");
        BigDecimal expectedBalance = TEST_BALANCE.add(delta);
        AccountInfoDto expected = new AccountInfoDto("Иванов Иван", "1990-01-01", expectedBalance);
        when(accountClient.changeBalance(eq(TEST_LOGIN), eq(delta))).thenReturn(expected);

        CompletableFuture<SendResult<String, NotificationEvent>> future = new CompletableFuture<>();
        future.complete(new SendResult<>(null, null));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        AccountInfoDto result = cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.PUT);

        assertThat(result).isEqualTo(expected);
        verify(accountClient, times(1)).changeBalance(eq(TEST_LOGIN), eq(delta));
        verify(kafkaTemplate, times(1)).send(eq("cash-notifications"), eq(TEST_LOGIN), any());
    }

    @Test
    void processCash_shouldDecreaseBalance_andSendToKafka_onGet() {
        BigDecimal delta = new BigDecimal("-500.00");
        BigDecimal expectedBalance = TEST_BALANCE.add(delta);
        AccountInfoDto expected = new AccountInfoDto("Иванов Иван", "1990-01-01", expectedBalance);
        when(accountClient.changeBalance(eq(TEST_LOGIN), eq(delta))).thenReturn(expected);

        CompletableFuture<SendResult<String, NotificationEvent>> future = new CompletableFuture<>();
        future.complete(new SendResult<>(null, null));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        AccountInfoDto result = cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.GET);

        assertThat(result).isEqualTo(expected);
        verify(accountClient, times(1)).changeBalance(eq(TEST_LOGIN), eq(delta));
        verify(kafkaTemplate, times(1)).send(eq("cash-notifications"), eq(TEST_LOGIN), any());
    }

    @Test
    void processCash_shouldThrowException_whenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, BigDecimal.valueOf(0), CashAction.PUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");

        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, BigDecimal.valueOf(-100), CashAction.GET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");

        verifyNoInteractions(accountClient);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void processCash_shouldThrowRuntimeException_whenAccountClientFails() {
        RuntimeException clientException = new RuntimeException("Service error");
        when(accountClient.changeBalance(anyString(), any())).thenThrow(clientException);

        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.PUT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ошибка при изменении баланса")
                .hasCause(clientException);

        verify(accountClient, times(1)).changeBalance(eq(TEST_LOGIN), eq(TEST_AMOUNT));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

        // Verify failure counter was incremented
        Counter failureCounter = meterRegistry.find("cash_withdrawal_failures")
                .tag("login", TEST_LOGIN)
                .tag("action", "PUT")
                .tag("reason", "RuntimeException")
                .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1);
    }

    @Test
    void processCash_withNullAction_shouldThrowExceptionAndNotCallAccountClient() {
        String login = "user";
        BigDecimal amount = BigDecimal.valueOf(100);

        assertThatThrownBy(() -> cashService.processCash(login, amount, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Action must not be null");

        verify(accountClient, never()).changeBalance(login, amount);
        verify(kafkaTemplate, never()).send("cash-notifications", login, null);
    }

    @Test
    void processCash_shouldRecordMetrics_onFailure() {
        when(accountClient.changeBalance(anyString(), any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.GET))
                .isInstanceOf(RuntimeException.class);

        // Verify metrics counter
        Counter counter = meterRegistry.find("cash_withdrawal_failures")
                .tag("login", TEST_LOGIN)
                .tag("action", "GET")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }
}
