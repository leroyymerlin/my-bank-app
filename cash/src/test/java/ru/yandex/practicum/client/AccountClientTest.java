package ru.yandex.practicum.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.dto.AccountInfoDto;

import java.math.BigDecimal;
import java.net.URI;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountClientTest {

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

    private AccountClient accountClient;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        accountClient = new AccountClient(webClientBuilder, "http://localhost:8080");
    }

    @Test
    void changeBalance_shouldReturnAccountInfo_onSuccess() {
        AccountInfoDto expected = new AccountInfoDto("Иванов Иван", "1990-01-01", new BigDecimal("1500.00"));

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri((Function<UriBuilder, URI>) any())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AccountInfoDto.class)).thenReturn(Mono.just(expected));

        AccountInfoDto result = accountClient.changeBalance("testuser", new BigDecimal("500"));

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Иванов Иван");
        assertThat(result.getBirthdate()).isEqualTo("1990-01-01");
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void changeBalance_shouldThrowException_onError() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri((Function<UriBuilder, URI>) any())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(AccountInfoDto.class))
                .thenReturn(Mono.error(new RuntimeException("Service error")));

        assertThatThrownBy(() -> accountClient.changeBalance("testuser", new BigDecimal("100")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Service error");
    }
}