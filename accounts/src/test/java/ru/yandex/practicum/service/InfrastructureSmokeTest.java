package ru.yandex.practicum.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.AccountsApplication;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {AccountsApplication.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.kafka.admin.auto-create=false",
                "spring.kafka.producer.bootstrap-servers=mock://localhost:9999",
                "spring.kafka.consumer.bootstrap-servers=mock://localhost:9999",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.enabled=false",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.endpoint.info.enabled=true",
                "management.endpoint.health.show-details=always",
                "management.endpoint.prometheus.enabled=true",
                "info.app.name=accounts-service-test",
                "management.tracing.enabled=false",
                "management.metrics.export.prometheus.enabled=true"
        }
)
@ActiveProfiles("test")
class InfrastructureSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Health endpoint должен возвращать 200")
    void healthEndpoint_shouldReturn200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("Info endpoint должен быть доступен")
    void infoEndpoint_shouldBeAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
