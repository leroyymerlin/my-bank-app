package ru.yandex.practicum.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.yandex.practicum.dto.NotificationRequest;

import java.time.Duration;

@Slf4j
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
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                .doOnSuccess(response -> log.info("Уведомление отправлено для {}", login))
                .doOnError(e -> log.error("Не удалось отправить уведомление для {}: {}", login, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
