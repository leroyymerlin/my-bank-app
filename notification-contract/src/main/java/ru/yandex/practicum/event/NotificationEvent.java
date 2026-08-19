package ru.yandex.practicum.event;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class NotificationEvent {
    UUID eventId;
    String eventVersion;
    String login;
    NotificationType type;
    String message;
    Instant occurredAt;
    String toLogin;
    String fromLogin;
    String amount;
}
