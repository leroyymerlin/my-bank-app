package ru.yandex.practicum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.AccountDto;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.exception.AccountNotFoundException;
import ru.yandex.practicum.repository.AccountRepository;

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

    /**
     * Изменение баланса (используется другими микросервисами).
     * @param login логин
     * @param delta изменение (положительное или отрицательное)
     */
    @Transactional
    public AccountInfoDto updateBalance(String login, int delta) {
        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Аккаунт не найден: " + login));

        int newBalance = account.getBalance() + delta;
        if (newBalance < 0) {
            throw new IllegalArgumentException("Недостаточно средств на счёте");
        }
        account.setBalance(newBalance);
        accountRepository.save(account);

        notificationClient.sendNotification(login, "Ваш баланс изменён на " + delta + ". Новый баланс: " + newBalance);

        return new AccountInfoDto(
                account.getName(),
                account.getBirthdate().format(DATE_FORMATTER),
                account.getBalance()
        );
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
