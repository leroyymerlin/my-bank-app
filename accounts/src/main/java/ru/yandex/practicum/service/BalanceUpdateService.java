package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.entity.Account;
import ru.yandex.practicum.exception.AccountNotFoundException;
import ru.yandex.practicum.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Сервис обновления баланса с защитой от optimistic locking.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceUpdateService {

    private final AccountRepository accountRepository;

    private static final BigDecimal MAX_BALANCE_DEC = new BigDecimal(1_000_000_000);

    /**
     * Обновление баланса в отдельной транзакции с retry.
     *
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class, StaleObjectStateException.class},
            maxAttemptsExpression = "${balance.update.retries:3}",
            backoff = @Backoff(
                    delayExpression = "${balance.update.backoff.delay:100}",
                    multiplierExpression = "${balance.update.backoff.multiplier:2.0}",
                    maxDelayExpression = "${balance.update.backoff.max:2000}"
            )
    )
    public AccountInfoDto doUpdateBalance(String login, BigDecimal delta) {
        log.info("Попытка обновления баланса для '{}', delta={}", login, delta);

        Account account = accountRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException("Аккаунт не найден: " + login));

        BigDecimal newBalance = account.getBalance().add(delta);
        validateBalance(newBalance);

        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Баланс обновлён для '{}': delta={}, новый={}",
                login, delta, newBalance);

        return new AccountInfoDto(
                account.getName(),
                account.getBirthdate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                account.getBalance()
        );
    }

    /**
     * Recover-метод для обработки окончательной ошибки после всех попыток.
     * Вызывается Spring Retry, когда все попытки исчерпаны.
     */
    @Recover
    public AccountInfoDto recoverFromOptimisticLock(OptimisticLockingFailureException e, String login, BigDecimal delta) {
        log.error("Не удалось обновить баланс для '{}', delta={} после всех попыток: {}",
                login, delta, e.getMessage());
        throw new RuntimeException("Не удалось обновить баланс из-за конкурентного доступа: " + login, e);
    }

    private void validateBalance(BigDecimal newBalance) {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Недостаточно средств на счёте");
        }
        if (newBalance.compareTo(MAX_BALANCE_DEC) > 0) {
            throw new IllegalArgumentException("Превышен максимальный баланс");
        }
    }
}
