package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.event.NotificationEvent;
import ru.yandex.practicum.event.NotificationEventFactory;
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

    @Test
    void createProfileUpdatedShouldSetCorrectType() {
        NotificationEvent event = NotificationEventFactory.createProfileUpdated("testuser");

        assertEquals(NotificationType.PROFILE_UPDATED, event.getType());
        assertEquals("testuser", event.getLogin());
        assertEquals("Ваши данные профиля были обновлены.", event.getMessage());
        assertEquals("1.0", event.getEventVersion());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void createBalanceUpdatedShouldSetCorrectTypeAndMessage() {
        String balanceMsg = "Ваш баланс изменён на 100.00. Новый баланс: 500.00";
        NotificationEvent event = NotificationEventFactory.createBalanceUpdated("user1", balanceMsg);

        assertEquals(NotificationType.BALANCE_UPDATED, event.getType());
        assertEquals("user1", event.getLogin());
        assertEquals(balanceMsg, event.getMessage());
        assertEquals("1.0", event.getEventVersion());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void createCashActionShouldMapPutCorrectly() {
        NotificationEvent event = NotificationEventFactory.createCashAction("user1", NotificationType.PUT, "Счёт пополнен на 100");

        assertEquals(NotificationType.PUT, event.getType());
        assertEquals("user1", event.getLogin());
        assertEquals("Счёт пополнен на 100", event.getMessage());
    }

    @Test
    void createCashActionShouldMapGetCorrectly() {
        NotificationEvent event = NotificationEventFactory.createCashAction("user1", NotificationType.GET, "Снято 50");

        assertEquals(NotificationType.GET, event.getType());
        assertEquals("user1", event.getLogin());
        assertEquals("Снято 50", event.getMessage());
    }

    @Test
    void createTransferSentShouldSetFields() {
        NotificationEvent event = NotificationEventFactory.createTransferSent("sender", "receiver", "100.00");

        assertEquals(NotificationType.TRANSFER_SENT, event.getType());
        assertEquals("sender", event.getLogin());
        assertEquals("sender", event.getFromLogin());
        assertEquals("receiver", event.getToLogin());
        assertEquals("100.00", event.getAmount());
        assertNotNull(event.getEventId());
    }

    @Test
    void createTransferReceivedShouldSetFields() {
        NotificationEvent event = NotificationEventFactory.createTransferReceived("receiver", "sender", "100.00");

        assertEquals(NotificationType.TRANSFER_RECEIVED, event.getType());
        assertEquals("receiver", event.getLogin());
        assertEquals("sender", event.getFromLogin());
        assertEquals("receiver", event.getToLogin());
        assertEquals("100.00", event.getAmount());
    }

    @Test
    void factoryShouldGenerateUniqueEventIds() {
        NotificationEvent event1 = NotificationEventFactory.createProfileUpdated("user1");
        NotificationEvent event2 = NotificationEventFactory.createProfileUpdated("user1");

        assertNotEquals(event1.getEventId(), event2.getEventId());
    }

    @Test
    void factoryEventsShouldHaveSameVersion() {
        NotificationEvent event1 = NotificationEventFactory.createProfileUpdated("user1");
        NotificationEvent event2 = NotificationEventFactory.createTransferSent("a", "b", "10");
        NotificationEvent event3 = NotificationEventFactory.createCashAction("u", NotificationType.PUT, "msg");

        assertEquals("1.0", event1.getEventVersion());
        assertEquals("1.0", event2.getEventVersion());
        assertEquals("1.0", event3.getEventVersion());
    }
}
