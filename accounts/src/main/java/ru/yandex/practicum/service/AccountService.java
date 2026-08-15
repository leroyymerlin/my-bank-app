package ru.yandex.practicum.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.AccountDto;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.exception.AccountNotFoundException;
import ru.yandex.practicum.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final NotificationClient notificationClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public AccountService(AccountRepository accountRepository, NotificationClient notificationClient) {
        this.accountRepository = accountRepository;
        this.notificationClient = notificationClient;
    }

    /**
     * Получение данных аккаунта по логину.
     */
    public AccountInfoDto getAccountInfo(String login) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Аккаунт не найден: " + login));
        return new AccountInfoDto(
                account.getName(),
                account.getBirthdate().format(DATE_FORMATTER),
                account.getBalance()
        );
    }

    /**
     * Возвращаем логин и имя для выбора получателя.
     */
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(a -> new AccountDto(a.getLogin(), a.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Обновление имени и даты рождения.
     * После обновления отправляем уведомление.
     */
    @Transactional
    public AccountInfoDto updateAccount(String login, String newName, LocalDate newBirthdate) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Аккаунт не найден: " + login));

        account.setName(newName);
        account.setBirthdate(newBirthdate);
        accountRepository.save(account);

        notificationClient.sendNotification(login, "Ваши данные профиля были обновлены.");

        return new AccountInfoDto(
                account.getName(),
                account.getBirthdate().format(DATE_FORMATTER),
                account.getBalance()
        );
    }

    private static final int MAX_BALANCE = 1_000_000_000;
    private static final BigDecimal MAX_BALANCE_DEC = new BigDecimal(MAX_BALANCE);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Изменение баланса (используется другими микросервисами).
     * @param login логин
     * @param delta изменение (положительное или отрицательное)
     */
    @Transactional
    public AccountInfoDto updateBalance(String login, BigDecimal delta) {
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return getAccountInfo(login);
        }

        if (delta.abs().compareTo(MAX_BALANCE_DEC) > 0) {
            throw new IllegalArgumentException("Сумма транзакции превышает максимальное значение");
        }

        int retries = 3;
        while (retries-- > 0) {
            try {
                Account account = accountRepository.findByLogin(login)
                        .orElseThrow(() -> new AccountNotFoundException("Аккаунт не найден: " + login));

                BigDecimal newBalance = account.getBalance().add(delta);
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Недостаточно средств на счёте");
                }
                if (newBalance.compareTo(MAX_BALANCE_DEC) > 0) {
                    throw new IllegalArgumentException("Превышен максимальный баланс");
                }
                account.setBalance(newBalance);
                account = accountRepository.save(account);

                notificationClient.sendNotification(login,
                        "Ваш баланс изменён на " + delta.setScale(2, BigDecimal.ROUND_HALF_UP) +
                                ". Новый баланс: " + account.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP));

                return new AccountInfoDto(
                        account.getName(),
                        account.getBirthdate().format(DATE_FORMATTER),
                        account.getBalance()
                );
            } catch (OptimisticLockingFailureException e) {
                if (retries == 0) {
                    throw new RuntimeException("Не удалось обновить баланс из-за конкурентного доступа", e);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {

                }
            }
        }
        throw new IllegalStateException("Неизвестная ошибка при обновлении баланса");
    }

    /**
     * Получение сущности аккаунта по логину (для внутреннего использования).
     */
    public Account getAccountEntity(String login) {
        return accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Аккаунт не найден: " + login));
    }

    /**
     * Проверка существования аккаунта.
     */
    public boolean existsByLogin(String login) {
        return accountRepository.existsByLogin(login);
    }
}
