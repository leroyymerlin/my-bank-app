package ru.yandex.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.dto.NotificationRequest;

import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(new ProcessedEventsStorage());
    }

    @Test
    void sendNotification_shouldNotThrowException() {
        NotificationRequest request = new NotificationRequest("testuser", "Test message");
        assertThatCode(() -> notificationService.sendNotification(request))
                .doesNotThrowAnyException();
    }
}
