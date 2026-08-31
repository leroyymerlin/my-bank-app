package ru.yandex.practicum.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnBean(ObservationRegistry.class)
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClientBuilder(
            OAuth2AuthorizedClientManager authorizedClientManager,
            ObservationRegistry observationRegistry) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("notification-client");

        return WebClient.builder()
                .observationRegistry(observationRegistry)
                .filter(oauth2Filter);
    }
}
