package ru.yandex.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.AccountDto;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.dto.UpdateAccountRequest;
import ru.yandex.practicum.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Получение данных текущего аккаунта (логин из JWT).
     */
    @GetMapping("/current")
    public AccountInfoDto getCurrentAccount(Authentication authentication) {
        String login = extractLogin(authentication);
        return accountService.getAccountInfo(login);
    }

    /**
     * Обновление данных текущего аккаунта.
     */
    @PutMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    public AccountInfoDto updateAccount(@RequestBody UpdateAccountRequest request,
                                        Authentication authentication) {
        String login = extractLogin(authentication);
        return accountService.updateAccount(login, request.getName(), request.getBirthdate());
    }

    /**
     * Получение списка всех аккаунтов (логин, имя).
     */
    @GetMapping
    public List<AccountDto> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    /**
     * Изменение баланса (для внутреннего вызова из Cash/Transfer).
     * Требует соответствующих прав (ROLE_INTERNAL).
     */
    @PostMapping("/balance")
    @ResponseStatus(HttpStatus.OK)
    public AccountInfoDto changeBalance(@RequestParam String login, @RequestParam int delta) {
        return accountService.updateBalance(login, delta);
    }

    /**
     * Вспомогательный метод для извлечения логина из JWT.
     */
    private String extractLogin(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String login = jwt.getClaim("preferred_username");
            if (login == null) {
                login = jwt.getSubject();
            }
            return login;
        }
        return authentication.getName();
    }
}
