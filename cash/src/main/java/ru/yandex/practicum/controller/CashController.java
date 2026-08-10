package ru.yandex.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.dto.CashRequest;
import ru.yandex.practicum.service.CashService;

@RestController
@RequestMapping("/api/cash")
public class CashController {

    private final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    /**
     * Обработка пополнения или снятия.
     * Логин извлекается из JWT-токена пользователя.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AccountInfoDto processCash(@RequestBody CashRequest request,
                                      Authentication authentication) {
        String login = extractLogin(authentication);
        return cashService.processCash(login, request.getAmount(), request.getAction());
    }

    private String extractLogin(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String login = jwt.getClaim("preferred_username");
            if (login == null) {
                login = jwt.getSubject();
            }
            return login;
        }
        throw new IllegalArgumentException("Не удалось извлечь логин из токена");
    }
}
