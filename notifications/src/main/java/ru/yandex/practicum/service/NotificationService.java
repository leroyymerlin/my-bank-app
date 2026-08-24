package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
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

    public NotificationService(ProcessedEventsStorage processedEventsStorage) {
        this.processedEventsStorage = processedEventsStorage;
    }

    /**
     * REST-метод для обратной совместимости.
     */
    public void sendNotification(NotificationRequest request) {
        log.info("Уведомление для пользователя '{}': {}", request.getLogin(), request.getMessage());
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
            ack.acknowledge();
            return;
        }

        processedEventsStorage.tryMarkAsProcessed(eventId);

        String targetLogin = event.getToLogin() != null ? event.getToLogin() : event.getLogin();
        log.info("Обработка уведомления для {}: type={}, message={}",
                targetLogin, event.getType(), event.getMessage());

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
