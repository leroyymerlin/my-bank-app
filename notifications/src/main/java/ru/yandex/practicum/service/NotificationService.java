package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.NotificationRequest;

@Service
@Slf4j
public class NotificationService {

    /**
     * Обработка сообщений из Kafka.
     */
    @KafkaListener(
            topics = {"cash-notifications", "transfer-notifications", "account-notifications"},
            groupId = "notifications-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleKafkaMessage(
            ConsumerRecord<String, Object> record,
            Acknowledgment ack) {
        try {
            Object value = record.value();
            String login;
            String message;
            String type;

            if (value instanceof NotificationRequest) {
                NotificationRequest req = (NotificationRequest) value;
                login = req.getLogin();
                message = req.getMessage();
                type = "HTTP";
            } else if (value instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
                login = (String) map.get("login");
                message = (String) map.get("message");
                type = (String) map.getOrDefault("type", "UNKNOWN");
            } else {
                log.warn("Неизвестный тип сообщения из Kafka: {}", value.getClass().getName());
                ack.acknowledge();
                return;
            }

            log.info("Kafka: Уведомление для '{}', type={}, topic={}, partition={}, offset={}, message='{}'",
                    login, type, record.topic(), record.partition(), record.offset(), message);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Ошибка обработки сообщения из Kafka: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            throw e;
        }
    }

    /**
     * REST-метод для обратной совместимости.
     */
    public void sendNotification(NotificationRequest request) {
        log.info("Уведомление для пользователя '{}': {}", request.getLogin(), request.getMessage());
    }
}
