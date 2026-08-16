package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.dto.TransferNotificationMessage;

import java.math.BigDecimal;

@Service
@Slf4j
public class TransferService {

    private final AccountClient accountClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransferService(AccountClient accountClient, KafkaTemplate<String, Object> kafkaTemplate) {
        this.accountClient = accountClient;
        this.kafkaTemplate = kafkaTemplate;
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

        String senderMessage = "Вы перевели " + formattedAmount +
                " пользователю " + toLogin +
                ". Новый баланс: " + senderAfterWithdraw.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP);
        String receiverMessage = "Вы получили перевод " + formattedAmount + " от " + fromLogin;

        TransferNotificationMessage senderNotification = TransferNotificationMessage.builder()
                .login(fromLogin)
                .message(senderMessage)
                .type("TRANSFER_SENT")
                .fromLogin(fromLogin)
                .toLogin(toLogin)
                .amount(formattedAmount.toString())
                .timestamp(System.currentTimeMillis())
                .build();

        TransferNotificationMessage receiverNotification = TransferNotificationMessage.builder()
                .login(toLogin)
                .message(receiverMessage)
                .type("TRANSFER_RECEIVED")
                .fromLogin(fromLogin)
                .toLogin(toLogin)
                .amount(formattedAmount.toString())
                .timestamp(System.currentTimeMillis())
                .build();

        String topic = "transfer-notifications";

        kafkaTemplate.send(topic, fromLogin, senderNotification)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки уведомления отправителю {}: {}", fromLogin, ex.getMessage());
                    } else {
                        log.info("Уведомление отправлено отправителю: topic={}, offset={}",
                                result.getProducerRecord().topic(), result.getRecordMetadata().offset());
                    }
                });

        kafkaTemplate.send(topic, toLogin, receiverNotification)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки уведомления получателю {}: {}", toLogin, ex.getMessage());
                    } else {
                        log.info("Уведомление отправлено получателю: topic={}, offset={}",
                                result.getProducerRecord().topic(), result.getRecordMetadata().offset());
                    }
                });

        return senderAfterWithdraw;
    }
}
