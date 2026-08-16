package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.dto.CashNotificationMessage;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;

@Service
@Slf4j
public class CashService {

    private final AccountClient accountClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CashService(AccountClient accountClient, KafkaTemplate<String, Object> kafkaTemplate) {
        this.accountClient = accountClient;
        this.kafkaTemplate = kafkaTemplate;
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

        String messageText = (action == CashAction.PUT)
                ? "Ваш счёт пополнен на " + amount.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Текущий баланс: " + updatedAccount.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP)
                : "Со счёта снято " + amount.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Текущий баланс: " + updatedAccount.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP);

        CashNotificationMessage notificationMessage = CashNotificationMessage.builder()
                .login(login)
                .message(messageText)
                .type(action.name())
                .timestamp(System.currentTimeMillis())
                .build();

        String topic = "cash-notifications";
        kafkaTemplate.send(topic, login, notificationMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки в Kafka для {}: {}", login, ex.getMessage());
                    } else {
                        log.info("Сообщение отправлено в Kafka: topic={}, partition={}, offset={}",
                                result.getProducerRecord().topic(),
                                result.getProducerRecord().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

        return updatedAccount;
    }
}
