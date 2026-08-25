package ru.yandex.practicum.event;

import java.time.Instant;
import java.util.UUID;

public final class NotificationEventFactory {

    private static final String VERSION = "1.0";

    private NotificationEventFactory() {}

    public static NotificationEvent createProfileUpdated(String login) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(VERSION)
                .login(login)
                .type(NotificationType.PROFILE_UPDATED)
                .message("Ваши данные профиля были обновлены.")
                .occurredAt(Instant.now())
                .build();
    }

    public static NotificationEvent createBalanceUpdated(String login, String balanceMessage) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(VERSION)
                .login(login)
                .type(NotificationType.BALANCE_UPDATED)
                .message(balanceMessage)
                .occurredAt(Instant.now())
                .build();
    }

    public static NotificationEvent createCashAction(String login, NotificationType type, String message) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(VERSION)
                .login(login)
                .type(type)
                .message(message)
                .occurredAt(Instant.now())
                .build();
    }

    public static NotificationEvent createTransferSent(String login, String toLogin, String amount) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(VERSION)
                .login(login)
                .type(NotificationType.TRANSFER_SENT)
                .occurredAt(Instant.now())
                .fromLogin(login)
                .toLogin(toLogin)
                .amount(amount)
                .build();
    }

    public static NotificationEvent createTransferReceived(String login, String fromLogin, String amount) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(VERSION)
                .login(login)
                .type(NotificationType.TRANSFER_RECEIVED)
                .occurredAt(Instant.now())
                .fromLogin(fromLogin)
                .toLogin(login)
                .amount(amount)
                .build();
    }
}
