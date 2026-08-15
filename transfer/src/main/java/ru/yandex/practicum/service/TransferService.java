package ru.yandex.practicum.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.client.NotificationClient;

import java.math.BigDecimal;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountClient accountClient;
    private final NotificationClient notificationClient;

    public TransferService(AccountClient accountClient, NotificationClient notificationClient) {
        this.accountClient = accountClient;
        this.notificationClient = notificationClient;
    }

    /**
     * Выполнение перевода между счетами.
     * @param fromLogin логин отправителя
     * @param toLogin   логин получателя
     * @param amount    сумма (положительное число)
     * @return обновлённые данные отправителя (баланс после списания)
     */
    public AccountInfoDto transfer(String fromLogin, String toLogin, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма перевода должна быть положительной");
        }
        if (fromLogin.equals(toLogin)) {
            throw new IllegalArgumentException("Нельзя перевести деньги самому себе");
        }

        BigDecimal formattedAmount = amount.setScale(2, BigDecimal.ROUND_HALF_UP);

        AccountInfoDto senderAfterWithdraw;
        try {
            senderAfterWithdraw = accountClient.changeBalance(fromLogin, formattedAmount.negate());
            log.info("Списано {} со счёта {}", formattedAmount, fromLogin);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при списании со счёта отправителя: " + e.getMessage(), e);
        }

        try {
            accountClient.changeBalance(toLogin, formattedAmount);
            log.info("Зачислено {} на счёт {}", formattedAmount, toLogin);
        } catch (Exception e) {
            log.error("Ошибка при зачислении получателю, выполняем откат", e);
            try {
                accountClient.changeBalance(fromLogin, formattedAmount);
                log.info("Выполнен откат: деньги возвращены отправителю {}", fromLogin);
            } catch (Exception rollbackEx) {
                log.error("Критическая ошибка: не удалось выполнить откат", rollbackEx);
                throw new RuntimeException("Не удалось завершить перевод и выполнить откат", rollbackEx);
            }
            throw new RuntimeException("Перевод не выполнен: ошибка при зачислении получателю", e);
        }

        try {
            notificationClient.sendNotification(fromLogin, "Вы перевели " + formattedAmount +
                    " пользователю " + toLogin +
                    ". Новый баланс: " + senderAfterWithdraw.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP));
            notificationClient.sendNotification(toLogin, "Вы получили перевод " + formattedAmount + " от " + fromLogin);
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомления, но перевод выполнен", e);
        }

        return senderAfterWithdraw;
    }
}
