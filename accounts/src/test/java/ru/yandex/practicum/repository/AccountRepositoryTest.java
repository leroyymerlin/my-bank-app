package ru.yandex.practicum.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.entity.Account;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    private static final String EXISTING_LOGIN = "testuser";
    private static final String ANOTHER_LOGIN = "anotherUser";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        Account account1 = new Account(1L, EXISTING_LOGIN, "Иванов Иван", LocalDate.of(1990, 1, 1), 1000);
        Account account2 = new Account(2L, ANOTHER_LOGIN, "Петров Пётр", LocalDate.of(1985, 5, 15), 500);
        accountRepository.saveAndFlush(account1);
        accountRepository.saveAndFlush(account2);
    }

    @Test
    void findByLogin_shouldReturnAccount_whenExists() {
        Optional<Account> found = accountRepository.findByLogin(EXISTING_LOGIN);

        assertThat(found).isPresent();
        assertThat(found.get().getLogin()).isEqualTo(EXISTING_LOGIN);
        assertThat(found.get().getName()).isEqualTo("Иванов Иван");
        assertThat(found.get().getBirthdate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(found.get().getBalance()).isEqualTo(1000);
    }

    @Test
    void existsByLogin_shouldReturnTrue_whenExists() {
        boolean exists = accountRepository.existsByLogin(EXISTING_LOGIN);
        assertThat(exists).isTrue();
    }
}