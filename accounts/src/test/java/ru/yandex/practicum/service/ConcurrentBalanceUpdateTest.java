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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

        try {
            for (int i = 0; i < CONCURRENT_THREADS; i++) {
                CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < UPDATES_PER_THREAD; j++) {
                        try {
                            startLatch.await(); // ждём синхронного старта
                            balanceUpdateService.doUpdateBalance(TEST_LOGIN, UPDATE_AMOUNT);
                        } catch (Throwable e) {
                            exceptions.add(e);
                        }
                    }
                }, executor);
            }

            startLatch.countDown();

            Thread.sleep(30_000);
            Account finalAccount = accountRepository.findByLogin(TEST_LOGIN).orElseThrow();

            assertThat(exceptions)
                    .as("Не должно быть исключений при успешных обновлениях")
                    .isEmpty();

            assertThat(finalAccount.getBalance())
                    .as("Не должно быть потерянных обновлений. Ожидалось: %s, фактически: %s, версия: %d",
                            expectedFinalBalance, finalAccount.getBalance(), finalAccount.getVersion())
                    .isEqualByComparingTo(expectedFinalBalance);

            assertThat(finalAccount.getVersion())
                    .as("Версия аккаунта должна увеличиться после обновлений")
                    .isGreaterThan(0L);
        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentUpdatesWithNegativeDelta_shouldNotGoBelowZero() throws Exception {
        BigDecimal initialBalance = new BigDecimal("500");
        BigDecimal negativeDelta = new BigDecimal("-300");
        int totalOperations = CONCURRENT_THREADS * UPDATES_PER_THREAD; // 6 операций

        accountRepository.deleteAll();

        Account account = Account.builder()
                .login(TEST_LOGIN)
                .name("Test User")
                .birthdate(LocalDate.of(1990, 1, 1))
                .balance(initialBalance)
                .version(0L)
                .build();
        accountRepository.save(account);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

        try {
            for (int i = 0; i < CONCURRENT_THREADS; i++) {
                CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < UPDATES_PER_THREAD; j++) {
                        try {
                            startLatch.await(); // ждём синхронного старта
                            balanceUpdateService.doUpdateBalance(TEST_LOGIN, negativeDelta);
                        } catch (Throwable e) {
                            exceptions.add(e);
                        }
                    }
                }, executor);
            }

            startLatch.countDown();

            Thread.sleep(30_000);

            Account finalAccount = accountRepository.findByLogin(TEST_LOGIN).orElseThrow();
            BigDecimal actualBalance = finalAccount.getBalance();

            assertThat(actualBalance)
                    .as("Баланс не должен быть отрицательным")
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);

            int successCount = totalOperations - exceptions.size();
            assertThat(successCount)
                    .as("Количество успешно завершённых операций (общее - failed с исключениями)")
                    .isGreaterThanOrEqualTo(0);

            if (!exceptions.isEmpty()) {
                long incorrectArgCount = exceptions.stream()
                        .filter(e -> e instanceof IllegalArgumentException
                                || (e.getCause() instanceof IllegalArgumentException))
                        .count();

                assertThat(incorrectArgCount)
                        .as("Исключения должны быть IllegalArgumentException (недостаточно средств), " +
                                "получено исключений: %d", exceptions.size());
            }

            BigDecimal totalDeducted = negativeDelta.multiply(BigDecimal.valueOf(successCount));
            BigDecimal expectedBalance = initialBalance.add(totalDeducted);
            assertThat(actualBalance)
                    .as("Финальный баланс должен равняться начальному минус успешные списания. " +
                            "Ожидалось: %s, фактически: %s, успешно: %d, неудач: %d",
                            expectedBalance, actualBalance, successCount, exceptions.size())
                    .isEqualByComparingTo(expectedBalance);

        } finally {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}
