package ru.yandex.practicum.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.dto.AccountInfoDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountClientTest {

    @Test
    void changeBalance_shouldReturnAccountInfo_onSuccess() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        String json = "{\"name\":\"Test\",\"birthdate\":\"2000-01-01\",\"balance\":1500}";
        ClientResponse response = ClientResponse.create(HttpStatusCode.valueOf(200))
                .header("Content-Type", "application/json")
                .body(json)
                .build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .exchangeFunction(exchangeFunction)
                .build();

        AccountInfoDto result = getAccountInfoDto(webClient);
        assertThat(result.getName()).isEqualTo("Test");
        assertThat(result.getBalance()).isEqualTo(1500);
    }

    private static AccountInfoDto getAccountInfoDto(WebClient webClient) {
        AccountClient client = new AccountClient(WebClient.builder(), "http://localhost:8080") {
            @Override
            public AccountInfoDto changeBalance(String login, int delta) {
                return webClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/accounts/balance")
                                .queryParam("login", login)
                                .queryParam("delta", delta)
                                .build())
                        .header("Content-Type", "application/json")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response ->
                                response.bodyToMono(String.class)
                                        .flatMap(error -> Mono.error(new RuntimeException("Ошибка Accounts: " + error)))
                        )
                        .bodyToMono(AccountInfoDto.class)
                        .block();
            }
        };

        return client.changeBalance("testuser", 500);
    }

}