package ru.yandex.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.AccountsApplication;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

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
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.kafka.admin.auto-create=false",
                "spring.kafka.producer.bootstrap-servers=mock://localhost:9999",
                "spring.kafka.consumer.bootstrap-servers=mock://localhost:9999",
                "spring.kafka.listener.auto-startup=false",
                "management.tracing.enabled=false"
        }
)
@ActiveProfiles("test")
class ConcurrentBalanceUpdateTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceUpdateService balanceUpdateService;

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
        int totalOperations = CONCURRENT_THREADS * UPDATES_PER_THREAD;
        BigDecimal expectedFinalBalance = INITIAL_BALANCE.add(
                UPDATE_AMOUNT.multiply(BigDecimal.valueOf(totalOperations)));

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

        List<CompletableFuture<Void>> futures = IntStream.range(0, CONCURRENT_THREADS)
                .mapToObj(threadIndex -> CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < UPDATES_PER_THREAD; j++) {
                        balanceUpdateService.doUpdateBalance(TEST_LOGIN, UPDATE_AMOUNT);
                    }
                }, executor))
                .toList();

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allDone.get(10, TimeUnit.SECONDS);

            Account finalAccount = accountRepository.findByLogin(TEST_LOGIN).orElseThrow();

            assertThat(finalAccount.getBalance())
                    .as("Не должно быть потерянных обновлений. Ожидалось: %s, фактически: %s, версия: %d",
                            expectedFinalBalance, finalAccount.getBalance(), finalAccount.getVersion())
                    .isEqualByComparingTo(expectedFinalBalance);

            assertThat(finalAccount.getVersion())
                    .as("Версия аккаунта должна увеличиться после обновлений")
                    .isEqualTo(totalOperations);
        } finally {
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentUpdatesWithNegativeDelta_shouldNotGoBelowZero() throws Exception {
        BigDecimal initialBalance = new BigDecimal("500");
        BigDecimal negativeDelta = new BigDecimal("-300");

        accountRepository.deleteAll();
        Account account = Account.builder()
                .login(TEST_LOGIN)
                .name("Test User")
                .birthdate(LocalDate.of(1990, 1, 1))
                .balance(initialBalance)
                .version(0L)
                .build();
        accountRepository.save(account);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        int totalOperations = CONCURRENT_THREADS * UPDATES_PER_THREAD;

        List<CompletableFuture<String>> operationResults = IntStream.range(0, CONCURRENT_THREADS)
                .mapToObj(threadIndex -> CompletableFuture.supplyAsync(() -> {
                    StringBuilder result = new StringBuilder();
                    for (int j = 0; j < UPDATES_PER_THREAD; j++) {
                        try {
                            balanceUpdateService.doUpdateBalance(TEST_LOGIN, negativeDelta);
                            result.append("success;");
                        } catch (RuntimeException e) {
                            Class<?> exceptionType = (e.getCause() != null) ? e.getCause().getClass() : e.getClass();
                            result.append("failed:|").append(exceptionType.getSimpleName()).append(";");
                        }
                    }
                    return result.toString();
                }, executor))
                .toList();

        CompletableFuture.allOf(operationResults.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        int successCount = 0;
        int failureCount = 0;
        boolean hasValidationOrLockException = false;

        for (CompletableFuture<String> future : operationResults) {
            String result = future.join();
            String[] operations = result.split(";");
            for (String op : operations) {
                if (op.isBlank()) continue;
                if (op.equals("success")) {
                    successCount++;
                } else if (op.startsWith("failed:")) {
                    failureCount++;
                    String exceptionType = op.substring("failed:".length());
                    if (exceptionType.contains("IllegalArgumentException") || exceptionType.contains("OptimisticLockException")) {
                        hasValidationOrLockException = true;
                    }
                }
            }
        }

        assertThat(successCount)
                .as("Должны быть успешные операции. Успешных: %d, неудачных: %d", successCount, failureCount)
                .isGreaterThan(0);
        assertThat(failureCount)
                .as("Должны быть неудачные операции (баланс < 0 или оптимистичная блокировка). Успешных: %d, неудачных: %d", successCount, failureCount)
                .isGreaterThan(0);
        assertThat(successCount + failureCount)
                .as("Общее число операций: %d, фактических: %d", totalOperations, successCount + failureCount)
                .isEqualTo(totalOperations);
        assertThat(hasValidationOrLockException)
                .as("Неудачные операции должны бросать IllegalArgumentException или OptimisticLockException")
                .isTrue();

        Account finalAccount = accountRepository.findByLogin(TEST_LOGIN).orElseThrow();
        BigDecimal actualBalance = finalAccount.getBalance();

        assertThat(actualBalance)
                .as("Баланс не должен быть отрицательным")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);

        BigDecimal expectedBalance = initialBalance.add(
                negativeDelta.multiply(BigDecimal.valueOf(successCount)));

        assertThat(actualBalance)
                .as("Финальный баланс: начальный=%s, успешных=%d, ожидалось=%s, фактически=%s",
                        initialBalance, successCount, expectedBalance, actualBalance)
                .isEqualByComparingTo(expectedBalance);

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);
    }
}
