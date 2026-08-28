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

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private TransferService transferService;

    private static final String FROM_LOGIN = "sender";
    private static final String TO_LOGIN = "recipient";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal BALANCE_AFTER_WITHDRAW = new BigDecimal("900.00");

    @BeforeEach
    void setUp() {
        transferService = new TransferService(accountClient, kafkaTemplate, meterRegistry);
        meterRegistry.clear();
    }

    @Test
    void transfer_shouldSucceed_whenValid() {
        AccountInfoDto senderAfter = new AccountInfoDto("Sender", "1990-01-01", BALANCE_AFTER_WITHDRAW);
        when(accountClient.changeBalance(FROM_LOGIN, AMOUNT.negate())).thenReturn(senderAfter);
        when(accountClient.changeBalance(TO_LOGIN, AMOUNT)).thenReturn(null);
        CompletableFuture<SendResult<String, NotificationEvent>> future = new CompletableFuture<>();
        future.complete(new SendResult<>(null, null));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        AccountInfoDto result = transferService.transfer(FROM_LOGIN, TO_LOGIN, AMOUNT);

        assertThat(result).isEqualTo(senderAfter);
        verify(accountClient).changeBalance(FROM_LOGIN, AMOUNT.negate());
        verify(accountClient).changeBalance(TO_LOGIN, AMOUNT);
        verify(kafkaTemplate, times(2)).send(eq("transfer-notifications"), anyString(), any());
    }

    @Test
    void transfer_shouldThrow_whenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, TO_LOGIN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма перевода должна быть положительной");
        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, TO_LOGIN, new BigDecimal("-10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма перевода должна быть положительной");
        verifyNoInteractions(accountClient, kafkaTemplate);
    }

    @Test
    void transfer_shouldThrow_whenSenderAndReceiverAreSame() {
        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, FROM_LOGIN, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Нельзя перевести деньги самому себе");
        verifyNoInteractions(accountClient, kafkaTemplate);
    }

    @Test
    void transfer_shouldRecordFailureCounter_onWithdrawalFailure() {
        when(accountClient.changeBalance(FROM_LOGIN, AMOUNT.negate()))
                .thenThrow(new RuntimeException("Insufficient funds"));

        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, TO_LOGIN, AMOUNT))
                .isInstanceOf(RuntimeException.class);

        Counter failureCounter = meterRegistry.find("transfer_failures")
                .tag("fromLogin", FROM_LOGIN)
                .tag("toLogin", TO_LOGIN)
                .tag("stage", "withdrawal")
                .tag("reason", "RuntimeException")
                .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1);
    }

    @Test
    void transfer_shouldRecordFailureCounter_onDepositFailure() {
        AccountInfoDto senderAfter = new AccountInfoDto("Sender", "1990-01-01", BALANCE_AFTER_WITHDRAW);
        when(accountClient.changeBalance(FROM_LOGIN, AMOUNT.negate())).thenReturn(senderAfter);
        when(accountClient.changeBalance(TO_LOGIN, AMOUNT))
                .thenThrow(new RuntimeException("Receiver account closed"));

        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, TO_LOGIN, AMOUNT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Перевод не выполнен");

        Counter failureCounter = meterRegistry.find("transfer_failures")
                .tag("fromLogin", FROM_LOGIN)
                .tag("toLogin", TO_LOGIN)
                .tag("stage", "deposit")
                .tag("reason", "RuntimeException")
                .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1);
    }
}
