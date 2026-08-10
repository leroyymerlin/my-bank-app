package ru.yandex.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.dto.TransferRequest;
import ru.yandex.practicum.service.TransferService;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AccountInfoDto transfer(@RequestBody TransferRequest request,
                                   Authentication authentication) {
        String fromLogin = extractLogin(authentication);
        return transferService.transfer(fromLogin, request.getToLogin(), request.getAmount());
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
