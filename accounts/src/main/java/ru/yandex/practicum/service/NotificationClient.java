package ru.yandex.practicum.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

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

    /**
     * Отправка уведомления о событии.
     * @param login      логин пользователя, которому отправляется уведомление
     * @param message    текст уведомления
     */
    public void sendNotification(String login, String message) {
        Map<String, String> body = Map.of(
                "login", login,
                "message", message
        );
        webClient.post()
                .uri("/api/notifications")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> {
                    System.err.println("Не удалось отправить уведомление: " + e.getMessage());
                    return Mono.empty();
                })
                .block();
    }
}
