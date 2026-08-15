package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.dto.AccountDto;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.exception.AccountNotFoundException;
import ru.yandex.practicum.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String TEST_LOGIN = "testuser";
    private static final String TEST_NAME = "Иванов Иван";
    private static final LocalDate TEST_BIRTHDATE = LocalDate.of(1990, 1, 1);
    private static final BigDecimal TEST_BALANCE = new BigDecimal("1000.00");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AccountService accountService;

    private Account createTestAccount() {
        return Account.builder()
                .id(1L)
                .login(TEST_LOGIN)
                .name(TEST_NAME)
                .birthdate(TEST_BIRTHDATE)
                .balance(TEST_BALANCE)
                .version(1L)
                .build();
    }

    @Test
    void getAccountInfo_shouldReturnAccountInfo_whenAccountExists() {
        Account account = createTestAccount();
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));

        AccountInfoDto result = accountService.getAccountInfo(TEST_LOGIN);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(TEST_NAME);
        assertThat(result.getBirthdate()).isEqualTo(TEST_BIRTHDATE.format(DATE_FORMATTER));
        assertThat(result.getBalance()).isEqualTo(TEST_BALANCE);
        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void getAccountInfo_shouldThrowAccountNotFoundException_whenAccountNotFound() {
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountInfo(TEST_LOGIN))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Аккаунт не найден: " + TEST_LOGIN);
        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void getAllAccounts_shouldReturnListOfAccountDtos() {
        Account account1 = Account.builder().id(1L).login("user1").name("Петров Пётр").birthdate(LocalDate.of(1985, 5, 15)).balance(new BigDecimal("500.00")).version(1L).build();
        Account account2 = Account.builder().id(1L).login("user2").name("Сидоров Сидор").birthdate(LocalDate.of(1995, 3, 10)).balance(new BigDecimal("200.00")).version(1L).build();
        when(accountRepository.findAll()).thenReturn(List.of(account1, account2));

        List<AccountDto> result = accountService.getAllAccounts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountDto::getLogin).containsExactly("user1", "user2");
        assertThat(result).extracting(AccountDto::getName).containsExactly("Петров Пётр", "Сидоров Сидор");
        verify(accountRepository, times(1)).findAll();
        verifyNoInteractions(notificationClient);
    }

    @Test
    void getAllAccounts_shouldReturnEmptyList_whenNoAccounts() {
        when(accountRepository.findAll()).thenReturn(List.of());

        List<AccountDto> result = accountService.getAllAccounts();

        assertThat(result).isEmpty();
        verify(accountRepository, times(1)).findAll();
        verifyNoInteractions(notificationClient);
    }

    @Test
    void updateAccount_shouldUpdateAndReturnAccountInfo() {
        Account account = createTestAccount();
        String newName = "Петров Пётр";
        LocalDate newBirthdate = LocalDate.of(1985, 5, 15);

        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        AccountInfoDto result = accountService.updateAccount(TEST_LOGIN, newName, newBirthdate);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(newName);
        assertThat(result.getBirthdate()).isEqualTo(newBirthdate.format(DATE_FORMATTER));
        assertThat(result.getBalance()).isEqualTo(TEST_BALANCE);

        assertThat(account.getName()).isEqualTo(newName);
        assertThat(account.getBirthdate()).isEqualTo(newBirthdate);

        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verify(accountRepository, times(1)).save(account);
        verify(notificationClient, times(1)).sendNotification(TEST_LOGIN, "Ваши данные профиля были обновлены.");
    }

    @Test
    void updateAccount_shouldThrowAccountNotFoundException_whenAccountNotFound() {
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateAccount(TEST_LOGIN, "Новое Имя", LocalDate.now()))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Аккаунт не найден: " + TEST_LOGIN);

        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verify(accountRepository, never()).save(any());
        verify(notificationClient, never()).sendNotification(anyString(), anyString());
    }

    @Test
    void updateAccount_shouldPropagateExceptionFromNotificationClient() {
        Account account = createTestAccount();
        String newName = "Петров Пётр";
        LocalDate newBirthdate = LocalDate.of(1985, 5, 15);

        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        doThrow(new RuntimeException("Ошибка отправки уведомления"))
                .when(notificationClient).sendNotification(anyString(), anyString());

        assertThatThrownBy(() -> accountService.updateAccount(TEST_LOGIN, newName, newBirthdate))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ошибка отправки уведомления");

        verify(accountRepository, times(1)).save(account);
        verify(notificationClient, times(1)).sendNotification(anyString(), anyString());
    }

    @Test
    void updateBalance_shouldIncreaseBalance_whenDeltaPositive() {
        Account account = createTestAccount();
        BigDecimal delta = new BigDecimal("500");
        BigDecimal expectedBalance = TEST_BALANCE.add(delta);

        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        AccountInfoDto result = accountService.updateBalance(TEST_LOGIN, delta);

        assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
        assertThat(account.getBalance()).isEqualByComparingTo(expectedBalance);

        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verify(accountRepository, times(1)).save(account);
        verify(notificationClient, times(1))
                .sendNotification(TEST_LOGIN, "Ваш баланс изменён на " + delta.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Новый баланс: " + expectedBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    @Test
    void updateBalance_shouldDecreaseBalance_whenDeltaNegativeAndSufficientFunds() {
        Account account = createTestAccount();
        BigDecimal delta = new BigDecimal("-300");
        BigDecimal expectedBalance = TEST_BALANCE.add(delta);

        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        AccountInfoDto result = accountService.updateBalance(TEST_LOGIN, delta);

        assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
        assertThat(account.getBalance()).isEqualByComparingTo(expectedBalance);

        verify(notificationClient, times(1))
                .sendNotification(TEST_LOGIN, "Ваш баланс изменён на " + delta.setScale(2, BigDecimal.ROUND_HALF_UP) +
                        ". Новый баланс: " + expectedBalance.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    @Test
    void updateBalance_shouldThrowIllegalArgumentException_whenInsufficientFunds() {
        Account account = createTestAccount();
        BigDecimal delta = new BigDecimal("-2000");
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.updateBalance(TEST_LOGIN, delta))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Недостаточно средств на счёте");

        assertThat(account.getBalance()).isEqualTo(TEST_BALANCE);
        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verify(accountRepository, never()).save(any());
        verify(notificationClient, never()).sendNotification(anyString(), anyString());
    }

    @Test
    void updateBalance_shouldThrowAccountNotFoundException_whenAccountNotFound() {
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateBalance(TEST_LOGIN, new BigDecimal("100")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Аккаунт не найден: " + TEST_LOGIN);

        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verify(accountRepository, never()).save(any());
        verify(notificationClient, never()).sendNotification(anyString(), anyString());
    }

    @Test
    void getAccountEntity_shouldReturnAccount_whenExists() {
        Account account = createTestAccount();
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.of(account));

        Account result = accountService.getAccountEntity(TEST_LOGIN);

        assertThat(result).isSameAs(account);
        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void getAccountEntity_shouldThrowAccountNotFoundException_whenNotFound() {
        when(accountRepository.findByLogin(TEST_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountEntity(TEST_LOGIN))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Аккаунт не найден: " + TEST_LOGIN);

        verify(accountRepository, times(1)).findByLogin(TEST_LOGIN);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void existsByLogin_shouldReturnTrue_whenAccountExists() {
        when(accountRepository.existsByLogin(TEST_LOGIN)).thenReturn(true);

        boolean exists = accountService.existsByLogin(TEST_LOGIN);

        assertThat(exists).isTrue();
        verify(accountRepository, times(1)).existsByLogin(TEST_LOGIN);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void existsByLogin_shouldReturnFalse_whenAccountDoesNotExist() {
        when(accountRepository.existsByLogin(TEST_LOGIN)).thenReturn(false);

        boolean exists = accountService.existsByLogin(TEST_LOGIN);

        assertThat(exists).isFalse();
        verify(accountRepository, times(1)).existsByLogin(TEST_LOGIN);
        verifyNoInteractions(notificationClient);
    }
}