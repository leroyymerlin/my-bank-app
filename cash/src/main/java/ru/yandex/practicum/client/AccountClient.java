package ru.yandex.practicum.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.dto.AccountInfoDto;

@Component
public class AccountClient {

    private final WebClient webClient;

    public AccountClient(WebClient.Builder webClientBuilder,
                         @Value("${account.service.url}") String accountServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(accountServiceUrl)
                .filter(new ServletOAuth2AuthorizedClientExchangeFilterFunction())
                .build();
    }

    /**
     * Изменение баланса пользователя.
     * @param login логин пользователя
     * @param delta изменение (может быть отрицательным)
     * @return обновлённые данные аккаунта
     */
    public AccountInfoDto changeBalance(String login, int delta) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/accounts/balance")
                        .queryParam("login", login)
                        .queryParam("delta", delta)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Ошибка Accounts: " + error)))
                )
                .bodyToMono(AccountInfoDto.class)
                .block();
    }
}
