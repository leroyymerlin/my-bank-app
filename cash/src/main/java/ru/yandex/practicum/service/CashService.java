package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.client.AccountClient;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.event.NotificationEvent;
import ru.yandex.practicum.event.NotificationEventFactory;
import ru.yandex.practicum.event.NotificationType;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;

@Service
@Slf4j
public class CashService {

    private final AccountClient accountClient;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public CashService(AccountClient accountClient,
                       KafkaTemplate<String, NotificationEvent> kafkaTemplate,
                       MeterRegistry meterRegistry) {
        this.accountClient = accountClient;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    public AccountInfoDto processCash(String login, BigDecimal amount, CashAction action) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login must not be empty");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (action == null) {
            throw new IllegalArgumentException("Action must not be null");
        }

        BigDecimal delta;
        NotificationType notificationType;
        String messageTemplate;

        switch (action) {
            case PUT:
                delta = amount;
                notificationType = NotificationType.PUT;
                messageTemplate = "Ваш счёт пополнен на %s. Текущий баланс: %s";
                break;
            case GET:
                delta = amount.negate();
                notificationType = NotificationType.GET;
                messageTemplate = "Со счёта снято %s. Текущий баланс: %s";
                break;
            default:
                throw new IllegalArgumentException("Unsupported action: " + action);
        }

        AccountInfoDto updatedAccount;
        try {
            updatedAccount = accountClient.changeBalance(login, delta);
        } catch (Exception e) {
            log.error("Ошибка при изменении баланса для {}: {}", login, e.getMessage());
            try {
                Counter.builder("cash_withdrawal_failures")
                        .tag("login", login)
                        .tag("action", action.name())
                        .tag("reason", e.getClass().getSimpleName())
                        .description("Количество неуспешных попыток снятия/пополнения денег")
                        .register(meterRegistry)
                        .increment();
            } catch (Exception metricsEx) {
                log.warn("Не удалось записать метрику: {}", metricsEx.getMessage());
            }
            throw new RuntimeException("Ошибка при изменении баланса: " + e.getMessage(), e);
        }

        String messageText = String.format(messageTemplate,
                amount.setScale(2, BigDecimal.ROUND_HALF_UP),
                updatedAccount.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP));

        NotificationEvent notificationEvent = NotificationEventFactory.createCashAction(login, notificationType, messageText);

        kafkaTemplate.send("cash-notifications", login, notificationEvent)
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
