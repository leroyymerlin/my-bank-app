package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.client.NotificationClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;

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
    private NotificationClient notificationClient;

    @InjectMocks
    private CashService cashService;

    private static final String TEST_LOGIN = "testuser";
    private static final BigDecimal TEST_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal TEST_BALANCE = new BigDecimal("1000.00");

    @Test
    void processCash_shouldIncreaseBalance_andSendNotification_onPut() {
        BigDecimal delta = new BigDecimal("500.00");
        BigDecimal expectedBalance = TEST_BALANCE.add(delta);
        AccountInfoDto expected = new AccountInfoDto("Иванов Иван", "1990-01-01", expectedBalance);
        when(accountClient.changeBalance(TEST_LOGIN, delta)).thenReturn(expected);

        AccountInfoDto result = cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.PUT);

        assertThat(result).isEqualTo(expected);
        verify(accountClient).changeBalance(TEST_LOGIN, delta);
        verify(notificationClient).sendNotification(TEST_LOGIN,
                "Ваш счёт пополнен на " + delta + ". Текущий баланс: " + expectedBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    @Test
    void processCash_shouldDecreaseBalance_andSendNotification_onGet() {
        BigDecimal delta = new BigDecimal("-500.00");
        BigDecimal expectedBalance = TEST_BALANCE.add(delta);
        AccountInfoDto expected = new AccountInfoDto("Иванов Иван", "1990-01-01", expectedBalance);
        when(accountClient.changeBalance(TEST_LOGIN, delta)).thenReturn(expected);

        AccountInfoDto result = cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.GET);

        assertThat(result).isEqualTo(expected);
        verify(accountClient).changeBalance(TEST_LOGIN, delta);
        verify(notificationClient).sendNotification(TEST_LOGIN,
                "Со счёта снято " + TEST_AMOUNT.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Текущий баланс: " + expectedBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    @Test
    void processCash_shouldThrowException_whenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, BigDecimal.valueOf(0), CashAction.PUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма должна быть положительной");

        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, BigDecimal.valueOf(-100), CashAction.GET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма должна быть положительной");

        verifyNoInteractions(accountClient);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void processCash_shouldThrowRuntimeException_whenAccountClientFails() {
        RuntimeException clientException = new RuntimeException("Service error");
        when(accountClient.changeBalance(anyString(), any())).thenThrow(clientException);

        assertThatThrownBy(() -> cashService.processCash(TEST_LOGIN, TEST_AMOUNT, CashAction.PUT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ошибка при изменении баланса")
                .hasCause(clientException);

        verify(accountClient).changeBalance(TEST_LOGIN, TEST_AMOUNT);
        verify(notificationClient, never()).sendNotification(anyString(), anyString());
    }
}