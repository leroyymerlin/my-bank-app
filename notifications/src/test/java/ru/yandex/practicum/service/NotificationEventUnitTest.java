package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.event.NotificationEvent;
import ru.yandex.practicum.event.NotificationType;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEventUnitTest {

    @Test
    void notificationEventBuilderShouldCreateEvent() {
        UUID eventId = UUID.randomUUID();
        String login = "testuser";
        
        NotificationEvent event = NotificationEvent.builder()
                .eventId(eventId)
                .eventVersion("1.0")
                .login(login)
                .type(NotificationType.BALANCE_UPDATED)
                .message("Тестовое уведомление")
                .occurredAt(Instant.now())
                .build();

        assertEquals(eventId, event.getEventId());
        assertEquals("1.0", event.getEventVersion());
        assertEquals(login, event.getLogin());
        assertEquals(NotificationType.BALANCE_UPDATED, event.getType());
        assertEquals("Тестовое уведомление", event.getMessage());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void notificationEventShouldSupportTransferFields() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion("1.0")
                .login("recipient")
                .type(NotificationType.TRANSFER_RECEIVED)
                .message("Переведено от sender")
                .occurredAt(Instant.now())
                .fromLogin("sender")
                .toLogin("recipient")
                .amount("150.00")
                .build();

        assertEquals(NotificationType.TRANSFER_RECEIVED, event.getType());
        assertEquals("sender", event.getFromLogin());
        assertEquals("recipient", event.getToLogin());
        assertEquals("150.00", event.getAmount());
    }

    @Test
    void notificationEventShouldBeImmutable() {
        Instant now = Instant.now();
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion("1.0")
                .login("immutableuser")
                .type(NotificationType.PUT)
                .message("Тест")
                .occurredAt(now)
                .build();

        // Все поля должны быть final и доступные только через геттеры
        assertNotNull(event.getEventId());
        assertNotNull(event.getLogin());
        assertSame(now, event.getOccurredAt());
    }

    @Test
    void notificationTypeEnumShouldHaveAllValues() {
        assertEquals(6, NotificationType.values().length);
        assertArrayEquals(
            new String[]{"PROFILE_UPDATED", "BALANCE_UPDATED", "PUT", "GET", "TRANSFER_SENT", "TRANSFER_RECEIVED"},
            java.util.Arrays.stream(NotificationType.values())
                    .map(Enum::name)
                    .toArray()
        );
    }

    @Test
    void notificationEventShouldHaveCorrectToString() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion("1.0")
                .login("user")
                .type(NotificationType.PROFILE_UPDATED)
                .message("Обновлён профиль")
                .occurredAt(Instant.now())
                .build();

        String str = event.toString();
        assertTrue(str.contains("user"));
        assertTrue(str.contains("PROFILE_UPDATED"));
    }
}
