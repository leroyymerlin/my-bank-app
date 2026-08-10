package ru.yandex.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationClientTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RequestBodySpec requestBodySpec;

    @Mock
    private RequestHeadersSpec requestHeadersSpec;

    @Mock
    private ResponseSpec responseSpec;

    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.filter(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        notificationClient = new NotificationClient(webClientBuilder, "http://localhost:8080");
    }

    @Test
    void sendNotification_shouldSendRequestWithCorrectBody() {
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);

        notificationClient.sendNotification("testuser", "Test message");

        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        Map<String, String> body = bodyCaptor.getValue();
        assertThat(body).containsEntry("login", "testuser");
        assertThat(body).containsEntry("message", "Test message");
    }

    @Test
    void sendNotification_shouldSetCorrectHeaders() {
        notificationClient.sendNotification("testuser", "Test message");

        verify(requestBodySpec).header("Content-Type", "application/json");
    }

    @Test
    void sendNotification_shouldCallRetrieveAndToBodilessEntity() {
        notificationClient.sendNotification("testuser", "Test message");

        verify(requestHeadersSpec).retrieve();
        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void sendNotification_shouldUseCorrectUri() {
        notificationClient.sendNotification("testuser", "Test message");

        verify(requestBodyUriSpec).uri("/api/notifications");
    }
}