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

import java.math.BigDecimal;

@Service
@Slf4j
public class TransferService {

    private final AccountClient accountClient;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public TransferService(AccountClient accountClient,
                           KafkaTemplate<String, NotificationEvent> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.accountClient = accountClient;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
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
            log.error("Ошибка при списании со счёта отправителя {}: {}", fromLogin, e.getMessage());
            try {
                Counter.builder("transfer_failures")
                        .tag("fromLogin", fromLogin)
                        .tag("toLogin", toLogin)
                        .tag("stage", "withdrawal")
                        .tag("reason", e.getClass().getSimpleName())
                        .description("Количество неуспешных попыток перевода денег (этапа списания)")
                        .register(meterRegistry)
                        .increment();
            } catch (Exception metricsEx) {
                log.warn("Не удалось записать метрику: {}", metricsEx.getMessage());
            }
            throw new RuntimeException("Ошибка при списании со счёта отправителя: " + e.getMessage(), e);
        }

        try {
            accountClient.changeBalance(toLogin, formattedAmount);
            log.info("Зачислено {} на счёт {}", formattedAmount, toLogin);
        } catch (Exception e) {
            log.error("Ошибка при зачислении получателю {}, выполняем откат", toLogin, e);
            try {
                Counter.builder("transfer_failures")
                        .tag("fromLogin", fromLogin)
                        .tag("toLogin", toLogin)
                        .tag("stage", "deposit")
                        .tag("reason", e.getClass().getSimpleName())
                        .description("Количество неуспешных попыток перевода денег (этапа зачисления)")
                        .register(meterRegistry)
                        .increment();
            } catch (Exception metricsEx) {
                log.warn("Не удалось записать метрику: {}", metricsEx.getMessage());
            }
            try {
                accountClient.changeBalance(fromLogin, formattedAmount);
                log.info("Выполнен откат: деньги возвращены отправителю {}", fromLogin);
            } catch (Exception rollbackEx) {
                log.error("Критическая ошибка: не удалось выполнить откат", rollbackEx);
                throw new RuntimeException("Не удалось завершить перевод и выполнить откат", rollbackEx);
            }
            throw new RuntimeException("Перевод не выполнен: ошибка при зачислении получателю", e);
        }

        NotificationEvent senderEvent = NotificationEventFactory.createTransferSent(
                fromLogin, toLogin, formattedAmount.toString());

        NotificationEvent receiverEvent = NotificationEventFactory.createTransferReceived(
                toLogin, fromLogin, formattedAmount.toString());

        String topic = "transfer-notifications";

        kafkaTemplate.send(topic, fromLogin, senderEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки уведомления отправителю {}: {}", fromLogin, ex.getMessage());
                    } else {
                        log.info("Уведомление отправлено отправителю: topic={}, offset={}",
                                result.getProducerRecord().topic(), result.getRecordMetadata().offset());
                    }
                });

        kafkaTemplate.send(topic, toLogin, receiverEvent)
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
