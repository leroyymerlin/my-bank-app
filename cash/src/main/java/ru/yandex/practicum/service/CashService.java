package ru.yandex.practicum.service;

import org.springframework.stereotype.Service;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.client.NotificationClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;

@Service
public class CashService {

    private final AccountClient accountClient;
    private final NotificationClient notificationClient;

    public CashService(AccountClient accountClient, NotificationClient notificationClient) {
        this.accountClient = accountClient;
        this.notificationClient = notificationClient;
    }

    /**
     * Выполнение операции пополнения или снятия.
     * @param login   логин пользователя (из JWT)
     * @param amount  сумма (положительное число)
     * @param action  PUT или GET
     * @return обновлённые данные аккаунта
     */
    public AccountInfoDto processCash(String login, BigDecimal amount, CashAction action) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма должна быть положительной");
        }

        BigDecimal delta = (action == CashAction.PUT) ? amount : amount.negate();

        AccountInfoDto updatedAccount;
        try {
            updatedAccount = accountClient.changeBalance(login, delta);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при изменении баланса: " + e.getMessage(), e);
        }

        String message = (action == CashAction.PUT)
                ? "Ваш счёт пополнен на " + amount.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Текущий баланс: " + updatedAccount.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP)
                : "Со счёта снято " + amount.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Текущий баланс: " + updatedAccount.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP);
        notificationClient.sendNotification(login, message);

        return updatedAccount;
    }
}
