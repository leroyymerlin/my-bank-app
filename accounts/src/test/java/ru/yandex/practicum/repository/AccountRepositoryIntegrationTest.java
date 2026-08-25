package ru.yandex.practicum.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.entity.Account;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=true"
})
@ActiveProfiles("test")
class AccountRepositoryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Создание аккаунта через Flyway-миграцию")
    void shouldCreateAccountViaMigration() {
        Account account = createTestAccount("user1", "Иванов Иван",
                LocalDate.of(1990, 1, 1), BigDecimal.ZERO);

        Account saved = accountRepository.saveAndFlush(account);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLogin()).isEqualTo("user1");
        assertThat(saved.getName()).isEqualTo("Иванов Иван");
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @DisplayName("Уникальный индекс на login")
    void shouldRejectDuplicateLogin() {
        Account account1 = createTestAccount("unique_user", "Иванов",
                LocalDate.of(1990, 1, 1), BigDecimal.ZERO);
        accountRepository.saveAndFlush(account1);

        Account account2 = createTestAccount("unique_user", "Петров",
                LocalDate.of(1990, 1, 1), BigDecimal.ZERO);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(account2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Optimistic locking работает")
    void optimisticLockingShouldWork() {
        Account account = createTestAccount("lock_user", "Иванов",
                LocalDate.of(1990, 1, 1), new BigDecimal("1000.00"));

        Account saved = accountRepository.saveAndFlush(account);
        long version = saved.getVersion();

        Account fromDb1 = accountRepository.findById(saved.getId()).orElseThrow();
        Account fromDb2 = accountRepository.findById(saved.getId()).orElseThrow();

        fromDb1.setBalance(new BigDecimal("1100.00"));
        accountRepository.saveAndFlush(fromDb1);

        fromDb2.setBalance(new BigDecimal("900.00"));
        assertThatThrownBy(() -> accountRepository.saveAndFlush(fromDb2))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Account finalAccount = accountRepository.findById(saved.getId()).orElseThrow();
        assertThat(finalAccount.getBalance()).isEqualByComparingTo("1100.00");
        assertThat(finalAccount.getVersion()).isEqualTo(version + 1);
    }

    @Test
    @DisplayName("Версия инкрементируется при каждом сохранении")
    void shouldIncrementVersionOnEachSave() {
        Account account = createTestAccount("version_user", "Тест",
                LocalDate.of(1990, 1, 1), BigDecimal.ZERO);

        Account saved = accountRepository.saveAndFlush(account);
        long version1 = saved.getVersion();

        saved.setName("Новое имя");
        Account saved2 = accountRepository.saveAndFlush(saved);
        long version2 = saved2.getVersion();

        assertThat(version2).isEqualTo(version1 + 1);
    }

    @Test
    @DisplayName("Поиск по логину")
    void shouldFindByLogin() {
        Account account = createTestAccount("find_user", "Иванов",
                LocalDate.of(1990, 1, 1), BigDecimal.ZERO);
        accountRepository.saveAndFlush(account);

        var found = accountRepository.findByLogin("find_user");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Иванов");
    }

    private Account createTestAccount(String login, String name,
                                      LocalDate birthdate, BigDecimal balance) {
        return Account.builder()
                .login(login)
                .name(name)
                .birthdate(birthdate)
                .balance(balance)
                .build();
    }
}
