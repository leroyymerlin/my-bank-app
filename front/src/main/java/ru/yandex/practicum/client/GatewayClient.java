package ru.yandex.practicum.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.yandex.practicum.model.AccountDto;
import ru.yandex.practicum.model.AccountInfo;
import ru.yandex.practicum.model.CashAction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class GatewayClient {

    private final RestClient restClient;
    private final OAuth2AuthorizedClientService authorizedClientService;

    protected RestClient getRestClient() {
        return restClient;
    }

    public GatewayClient(RestClient.Builder restClientBuilder,
                         @Value("${gateway.url}") String gatewayUrl,
                         OAuth2AuthorizedClientService authorizedClientService) {
        this.restClient = restClientBuilder.baseUrl(gatewayUrl).build();
        this.authorizedClientService = authorizedClientService;
    }

    private String extractToken(Authentication auth) {
        if (auth instanceof OAuth2AuthenticationToken token) {
            String registrationId = token.getAuthorizedClientRegistrationId();
            String userName = token.getName();

            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    registrationId, userName
            );

            if (authorizedClient == null) {
                throw new IllegalStateException("No authorized client found for user: " + userName);
            }

            return authorizedClient.getAccessToken().getTokenValue();
        }
        throw new IllegalStateException("Authentication is not OAuth2");
    }

    /**
     * Получение данных текущего аккаунта.
     */
    public AccountInfo getCurrentAccount(Authentication auth) {
        String token = extractToken(auth);
        return restClient.get()
                .uri("/api/accounts/current")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new RuntimeException("Ошибка получения аккаунта: " + response.getBody());
                })
                .body(AccountInfo.class);
    }

    /**
     * Список всех аккаунтов для выбора получателя.
     */
    public List<AccountDto> getAccounts(Authentication auth) {
        String token = extractToken(auth);
        AccountDto[] accounts = restClient.get()
                .uri("/api/accounts")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new RuntimeException("Ошибка получения списка аккаунтов: " + response.getBody());
                })
                .body(new ParameterizedTypeReference<AccountDto[]>() {});
        return accounts != null ? List.of(accounts) : List.of();
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
        return restClient.put()
                .uri("/api/accounts/update")
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new RuntimeException("Ошибка обновления данных: " + response.getBody());
                })
                .body(AccountInfo.class);
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
        return restClient.post()
                .uri("/api/cash")
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new RuntimeException("Ошибка операции с деньгами: " + response.getBody());
                })
                .body(AccountInfo.class);
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
        return restClient.post()
                .uri("/api/transfer")
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new RuntimeException("Ошибка перевода: " + response.getBody());
                })
                .body(AccountInfo.class);
    }
}