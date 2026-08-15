package ru.yandex.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.NotificationRequest;
import ru.yandex.practicum.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Приём уведомления от других микросервисов.
     * Вызывается по Client Credentials (внутренние сервисы).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendNotification(@RequestBody NotificationRequest request) {
        notificationService.sendNotification(request);
    }
}
