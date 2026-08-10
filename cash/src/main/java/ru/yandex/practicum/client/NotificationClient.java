package ru.yandex.practicum.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.dto.NotificationRequest;

@Component
public class NotificationClient {

    private final WebClient webClient;

    public NotificationClient(WebClient.Builder webClientBuilder,
                              @Value("${notification.service.url}") String notificationUrl) {
        this.webClient = webClientBuilder
                .baseUrl(notificationUrl)
                .filter(new ServletOAuth2AuthorizedClientExchangeFilterFunction())
                .build();
    }

    public void sendNotification(String login, String message) {
        NotificationRequest request = new NotificationRequest(login, message);
        webClient.post()
                .uri("/api/notifications")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> {
                    System.err.println("Не удалось отправить уведомление: " + e.getMessage());
                    return Mono.empty();
                })
                .block();
    }
}
