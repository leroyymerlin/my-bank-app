package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.client.NotificationClient;
import ru.yandex.practicum.dto.AccountInfoDto;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private TransferService transferService;

    private static final String FROM_LOGIN = "sender";
    private static final String TO_LOGIN = "recipient";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal BALANCE_AFTER_WITHDRAW = new BigDecimal("900.00");

    @Test
    void transfer_shouldSucceed_whenValid() {
        AccountInfoDto senderAfter = new AccountInfoDto("Sender", "1990-01-01", BALANCE_AFTER_WITHDRAW);
        when(accountClient.changeBalance(FROM_LOGIN, AMOUNT.negate())).thenReturn(senderAfter);
        when(accountClient.changeBalance(TO_LOGIN, AMOUNT)).thenReturn(null);

        AccountInfoDto result = transferService.transfer(FROM_LOGIN, TO_LOGIN, AMOUNT);

        assertThat(result).isEqualTo(senderAfter);
        verify(accountClient).changeBalance(FROM_LOGIN, AMOUNT.negate());
        verify(accountClient).changeBalance(TO_LOGIN, AMOUNT);
        verify(notificationClient).sendNotification(eq(FROM_LOGIN), contains("100.00"));
        verify(notificationClient).sendNotification(eq(TO_LOGIN), contains("100.00"));
    }

    @Test
    void transfer_shouldThrow_whenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, TO_LOGIN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма перевода должна быть положительной");
        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, TO_LOGIN, new BigDecimal("-10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма перевода должна быть положительной");
        verifyNoInteractions(accountClient, notificationClient);
    }

    @Test
    void transfer_shouldThrow_whenSenderAndReceiverAreSame() {
        assertThatThrownBy(() -> transferService.transfer(FROM_LOGIN, FROM_LOGIN, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Нельзя перевести деньги самому себе");
        verifyNoInteractions(accountClient, notificationClient);
    }
}