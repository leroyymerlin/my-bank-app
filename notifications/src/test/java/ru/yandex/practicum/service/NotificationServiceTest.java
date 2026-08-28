package ru.yandex.practicum.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import ru.yandex.practicum.dto.NotificationRequest;
import ru.yandex.practicum.event.NotificationEvent;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationServiceTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ProcessedEventsStorage processedEventsStorage = new ProcessedEventsStorage();
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        meterRegistry.clear();
        processedEventsStorage.clear();
        notificationService = new NotificationService(processedEventsStorage, meterRegistry);
    }

    @Test
    void sendNotification_shouldNotThrowException() {
        NotificationRequest request = new NotificationRequest("testuser", "Test message");
        assertThatCode(() -> notificationService.sendNotification(request))
                .doesNotThrowAnyException();
    }

    @Test
    void handleNotification_shouldProcessNewEventAndAck() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .login("testuser")
                .type(ru.yandex.practicum.event.NotificationType.PUT)
                .message("Test notification")
                .build();

        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);

        notificationService.handleNotification(event, "cash-notifications", ack);

        assertThat(processedEventsStorage.contains(event.getEventId().toString())).isTrue();
        org.mockito.Mockito.verify(ack).acknowledge();

        Counter counter = meterRegistry.find("notification_events_total")
                .tag("login", "testuser")
                .tag("event_type", "PUT")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void handleNotification_shouldSkipDuplicateEventAndAck() {
        String eventId = UUID.randomUUID().toString();
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.fromString(eventId))
                .login("testuser")
                .type(ru.yandex.practicum.event.NotificationType.GET)
                .message("First event")
                .build();

        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);

        notificationService.handleNotification(event, "cash-notifications", ack);
        org.mockito.Mockito.verify(ack).acknowledge();

        org.mockito.Mockito.reset(ack);

        notificationService.handleNotification(event, "cash-notifications", ack);
        org.mockito.Mockito.verify(ack).acknowledge();

        Counter duplicateCounter = meterRegistry.find("notification_send_failures")
                .tag("login", "testuser")
                .tag("reason", "duplicate_event")
                .counter();
        assertThat(duplicateCounter).isNotNull();
        assertThat(duplicateCounter.count()).isEqualTo(1);
    }

    @Test
    void handleNotification_shouldProcessTransferEvent() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .toLogin("recipient")
                .login("sender")
                .type(ru.yandex.practicum.event.NotificationType.TRANSFER_RECEIVED)
                .message("Transfer received")
                .build();

        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);

        notificationService.handleNotification(event, "transfer-notifications", ack);

        org.mockito.Mockito.verify(ack).acknowledge();

        Counter counter = meterRegistry.find("notification_events_total")
                .tag("login", "recipient")
                .tag("event_type", "TRANSFER_RECEIVED")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void handleNotification_shouldProcessAccountEvent() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID())
                .login("testuser")
                .type(ru.yandex.practicum.event.NotificationType.PROFILE_UPDATED)
                .message("Profile updated")
                .build();

        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);

        notificationService.handleNotification(event, "account-notifications", ack);

        org.mockito.Mockito.verify(ack).acknowledge();

        Counter counter = meterRegistry.find("notification_events_total")
                .tag("login", "testuser")
                .tag("event_type", "PROFILE_UPDATED")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void handleNotification_shouldHandleNullEventId() {
        NotificationEvent event = NotificationEvent.builder()
                .login("testuser")
                .type(ru.yandex.practicum.event.NotificationType.PUT)
                .message("No event ID")
                .build();

        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);

        assertThatCode(() -> notificationService.handleNotification(event, "cash-notifications", ack))
                .doesNotThrowAnyException();

        org.mockito.Mockito.verify(ack).acknowledge();
    }

    @Test
    void handleDlt_shouldLogAndAck() {
        Acknowledgment ack = org.mockito.Mockito.mock(Acknowledgment.class);

        assertThatCode(() -> notificationService.handleDlt("test-key", "test-payload", ack))
                .doesNotThrowAnyException();

        org.mockito.Mockito.verify(ack).acknowledge();
    }
}
