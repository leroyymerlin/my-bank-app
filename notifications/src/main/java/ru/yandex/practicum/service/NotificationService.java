package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.NotificationRequest;
import ru.yandex.practicum.event.NotificationEvent;

@Service
@Slf4j
public class NotificationService {

    private final ProcessedEventsStorage processedEventsStorage;
    private final MeterRegistry meterRegistry;

    public NotificationService(ProcessedEventsStorage processedEventsStorage, MeterRegistry meterRegistry) {
        this.processedEventsStorage = processedEventsStorage;
        this.meterRegistry = meterRegistry;
    }

    /**
     * REST-метод для обратной совместимости.
     */
    public void sendNotification(NotificationRequest request) {
        log.info("Уведомление для пользователя '{}': {}", request.getLogin(), request.getMessage());
    }

    private void recordSuccessfulNotification(String targetLogin, String eventType) {
        try {
            Counter.builder("notification_events_total")
                    .tag("login", targetLogin)
                    .tag("event_type", eventType)
                    .description("Количество успешно обработанных уведомлений")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception metricsEx) {
            log.warn("Не удалось записать метрику успешного события: {}", metricsEx.getMessage());
        }
    }

    private void recordFailedNotification(String targetLogin, String failureReason) {
        try {
            Counter.builder("notification_send_failures")
                    .tag("login", targetLogin)
                    .tag("reason", failureReason)
                    .description("Количество неуспешных попыток обработки уведомления")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception metricsEx) {
            log.warn("Не удалось записать метрику неудачного события: {}", metricsEx.getMessage());
        }
    }

    @KafkaListener(
            topics = {"account-notifications", "cash-notifications", "transfer-notifications"},
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleNotification(
            @Payload NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        String eventId = event.getEventId() != null ? event.getEventId().toString() : null;

        if (processedEventsStorage.contains(eventId)) {
            log.info("Дубликат события {}, пропускаем", eventId);
            String targetLogin = event.getToLogin() != null ? event.getToLogin() : event.getLogin();
            recordFailedNotification(targetLogin, "duplicate_event");
            ack.acknowledge();
            return;
        }

        processedEventsStorage.tryMarkAsProcessed(eventId);

        String targetLogin = event.getToLogin() != null ? event.getToLogin() : event.getLogin();
        log.info("Обработка уведомления для {}: type={}, message={}",
                targetLogin, event.getType(), event.getMessage());

        recordSuccessfulNotification(targetLogin, event.getType() != null ? event.getType().name() : "UNKNOWN");

        ack.acknowledge();
    }

    @KafkaListener(
            topics = "${kafka.dlt.name:notifications-service.dlt}",
            containerFactory = "dltListenerContainerFactory"
    )
    public void handleDlt(String key, String payload, Acknowledgment ack) {
        log.error("Сообщение попало в DLT. key={}, payload={}", key, payload);
        ack.acknowledge();
    }
}
