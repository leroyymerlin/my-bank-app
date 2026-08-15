package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.NotificationRequest;

@Service
@Slf4j
public class NotificationService {

    /**
     * Отправка уведомления пользователю.
     * В текущей реализации просто логируем, но можно добавить email, SMS и т.д.
     */
    public void sendNotification(NotificationRequest request) {
        log.info("Уведомление для пользователя '{}': {}", request.getLogin(), request.getMessage());
    }
}
