package ru.yandex.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.AccountsApplication;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {AccountsApplication.class},
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=true",
                "spring.kafka.producer.bootstrap-servers=localhost:9092",
                "spring.kafka.consumer.bootstrap-servers=localhost:9092"
        }
)
@ActiveProfiles("test")
class ConcurrentBalanceUpdateTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceUpdateService balanceUpdateService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TEST_LOGIN = "testuser";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000.00");
    private static final BigDecimal UPDATE_AMOUNT = new BigDecimal("100");
    private static final int CONCURRENT_THREADS = 3;
    private static final int UPDATES_PER_THREAD = 2;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        Account account = Account.builder()
                .login(TEST_LOGIN)
                .name("Test User")
                .birthdate(LocalDate.of(1990, 1, 1))
                .balance(INITIAL_BALANCE)
                .version(0L)
                .build();
        accountRepository.save(account);
    }

    @Test
    void concurrentUpdates_shouldNotLoseUpdates() throws Exception {
        int totalUpdates = CONCURRENT_THREADS * UPDATES_PER_THREAD;
        BigDecimal expectedFinalBalance = INITIAL_BALANCE.add(
                UPDATE_AMOUNT.multiply(BigDecimal.valueOf(totalUpdates)));

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < UPDATES_PER_THREAD; j++) {
                    try {
                        balanceUpdateService.doUpdateBalance(TEST_LOGIN, UPDATE_AMOUNT);
                    } catch (Exception e) {
                        throw new RuntimeException("Concurrent update failed", e);
                    }
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allDone.get(30, TimeUnit.SECONDS);

        Account finalAccount = accountRepository.findByLogin(TEST_LOGIN).orElseThrow();
        BigDecimal actualBalance = finalAccount.getBalance();

        assertThat(actualBalance)
                .as("Не должно быть потерянных обновлений. Ожидалось: %s, фактически: %s, версия: %d",
                        expectedFinalBalance, actualBalance, finalAccount.getVersion())
                .isEqualByComparingTo(expectedFinalBalance);
    }

    @Test
    void concurrentUpdatesWithNegativeDelta_shouldNotGoBelowZero() throws Exception {
        int totalUpdates = CONCURRENT_THREADS * UPDATES_PER_THREAD;

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int[] successCount = {0};
        int[] failCount = {0};

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < UPDATES_PER_THREAD; j++) {
                    try {
                        balanceUpdateService.doUpdateBalance(TEST_LOGIN, new BigDecimal("-200"));
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    } catch (Exception e) {
                        synchronized (failCount) {
                            failCount[0]++;
                        }
                    }
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allDone.get(30, TimeUnit.SECONDS);

        Account finalAccount = accountRepository.findByLogin(TEST_LOGIN).orElseThrow();
        assertThat(finalAccount.getBalance())
                .as("Баланс не должен быть отрицательным")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);

        assertThat(successCount[0] + failCount[0])
                .as("Все обновления должны быть обработаны")
                .isEqualTo(totalUpdates);
    }
}
