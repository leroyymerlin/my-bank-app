package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.NotificationRequest;

@Service
@Slf4j
public class NotificationService {

    /**
     * REST-метод для обратной совместимости.
     */
    public void sendNotification(NotificationRequest request) {
        log.info("Уведомление для пользователя '{}': {}", request.getLogin(), request.getMessage());
    }
}
