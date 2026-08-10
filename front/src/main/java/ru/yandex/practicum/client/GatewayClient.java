package ru.yandex.practicum.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.model.AccountDto;
import ru.yandex.practicum.model.AccountInfo;
import ru.yandex.practicum.model.CashAction;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class GatewayClient {

    private final WebClient webClient;

    public GatewayClient(WebClient.Builder webClientBuilder,
                         @Value("${gateway.url}") String gatewayUrl) {
        this.webClient = webClientBuilder.baseUrl(gatewayUrl).build();
    }

    private String extractToken(Authentication auth) {
        if (auth instanceof OAuth2AuthenticationToken token) {
            return token.getName();
        }
        throw new IllegalStateException("Authentication is not OAuth2");
    }

    /**
     * Получение данных текущего аккаунта.
     */
    public AccountInfo getCurrentAccount(Authentication auth) {
        String token = extractToken(auth);
        return webClient.get()
                .uri("/api/accounts/current")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Ошибка получения аккаунта: " + error)))
                )
                .bodyToMono(AccountInfo.class)
                .block();
    }

    /**
     * Список всех аккаунтов для выбора получателя.
     */
    public List<AccountDto> getAccounts(Authentication auth) {
        String token = extractToken(auth);
        AccountDto[] accounts = webClient.get()
                .uri("/api/accounts")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Ошибка получения списка аккаунтов: " + error)))
                )
                .bodyToMono(AccountDto[].class)
                .block();
        return accounts != null ? Arrays.asList(accounts) : List.of();
    }

    /**
     * Обновление имени и даты рождения.
     */
    public AccountInfo updateAccount(Authentication auth, String name, LocalDate birthdate) {
        String token = extractToken(auth);
        Map<String, Object> body = Map.of(
                "name", name,
                "birthdate", birthdate.toString()
        );
        return webClient.post()
                .uri("/api/accounts/update")
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Ошибка обновления данных: " + error)))
                )
                .bodyToMono(AccountInfo.class)
                .block();
    }

    /**
     * Пополнение или снятие средств.
     */
    public AccountInfo cashAction(Authentication auth, int amount, CashAction action) {
        String token = extractToken(auth);
        Map<String, Object> body = Map.of(
                "amount", amount,
                "action", action.name()
        );
        return webClient.post()
                .uri("/api/cash")
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Ошибка операции с деньгами: " + error)))
                )
                .bodyToMono(AccountInfo.class)
                .block();
    }

    /**
     * Перевод средств другому аккаунту.
     */
    public AccountInfo transfer(Authentication auth, int amount, String toLogin) {
        String token = extractToken(auth);
        Map<String, Object> body = Map.of(
                "amount", amount,
                "toLogin", toLogin
        );
        return webClient.post()
                .uri("/api/transfer")
                .header("Authorization", "Bearer " + token)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Ошибка перевода: " + error)))
                )
                .bodyToMono(AccountInfo.class)
                .block();
    }
}