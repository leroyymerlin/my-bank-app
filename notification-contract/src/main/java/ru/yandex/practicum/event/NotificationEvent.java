package ru.yandex.practicum.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public static NotificationEvent of(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("eventVersion") String eventVersion,
            @JsonProperty("login") String login,
            @JsonProperty("type") NotificationType type,
            @JsonProperty("message") String message,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("toLogin") String toLogin,
            @JsonProperty("fromLogin") String fromLogin,
            @JsonProperty("amount") String amount) {
        return NotificationEvent.builder()
                .eventId(eventId)
                .eventVersion(eventVersion)
                .login(login)
                .type(type)
                .message(message)
                .occurredAt(occurredAt)
                .toLogin(toLogin)
                .fromLogin(fromLogin)
                .amount(amount)
                .build();
    }
}
