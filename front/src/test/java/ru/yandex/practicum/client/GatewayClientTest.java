package ru.yandex.practicum.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.model.AccountInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayClientSimpleTest {

    @Test
    void getCurrentAccount_shouldReturnAccountInfo() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        String json = "{\"name\":\"Test\",\"birthdate\":\"2000-01-01\",\"balance\":100}";
        ClientResponse response = ClientResponse.create(HttpStatusCode.valueOf(200))
                .header("Content-Type", "application/json")
                .body(json)
                .build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .exchangeFunction(exchangeFunction)
                .build();

        GatewayClient client = new GatewayClient(WebClient.builder(), "http://localhost:8080") {
            @Override
            public AccountInfo getCurrentAccount(org.springframework.security.core.Authentication auth) {
                return webClient.get()
                        .uri("/api/accounts/current")
                        .header("Authorization", "Bearer token")
                        .retrieve()
                        .bodyToMono(AccountInfo.class)
                        .block();
            }
        };

        AccountInfo result = client.getCurrentAccount(null);
        assertThat(result.getName()).isEqualTo("Test");
        assertThat(result.getBalance()).isEqualTo(100);
    }
}