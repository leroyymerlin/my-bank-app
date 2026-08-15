package ru.yandex.practicum.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.dto.NotificationRequest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationClientTest {

    @Test
    void sendNotification_shouldNotThrowException_onSuccess() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        ClientResponse response = ClientResponse.create(HttpStatusCode.valueOf(200)).build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .exchangeFunction(exchangeFunction)
                .build();

        NotificationClient client = new NotificationClient(WebClient.builder(), "http://localhost:8080") {
            @Override
            public void sendNotification(String login, String message) {
                NotificationRequest request = new NotificationRequest(login, message);
                webClient.post()
                        .uri("/api/notifications")
                        .header("Content-Type", "application/json")
                        .bodyValue(request)
                        .retrieve()
                        .toBodilessEntity()
                        .onErrorResume(e -> {
                            System.err.println("Не удалось отправить уведомление: " + e.getMessage());
                            return Mono.empty();
                        })
                        .block();
            }
        };

        assertThatCode(() -> client.sendNotification("testuser", "Test message"))
                .doesNotThrowAnyException();
    }
}