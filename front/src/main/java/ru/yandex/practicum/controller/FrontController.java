package ru.yandex.practicum.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.client.GatewayClient;
import ru.yandex.practicum.model.AccountDto;
import ru.yandex.practicum.model.AccountInfo;
import ru.yandex.practicum.model.CashAction;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
public class FrontController {

    @Autowired
    private GatewayClient gatewayClient;

    @GetMapping("/")
    public String index() {
        return "redirect:/account";
    }

    @GetMapping("/account")
    public String getAccount(Model model, Authentication authentication) {
        try {
            AccountInfo info = gatewayClient.getCurrentAccount(authentication);
            List<AccountDto> accounts = gatewayClient.getAccounts(authentication);
            fillModel(model, info, accounts, null, null);
        } catch (Exception e) {
            model.addAttribute("errors", List.of("Не удалось загрузить данные: " + e.getMessage()));
        }
        return "main";
    }

    @PostMapping("/account")
    public String editAccount(
            Model model,
            @RequestParam("name") String name,
            @RequestParam("birthdate") String birthdate,
            Authentication authentication
    ) {
        List<String> errors = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            errors.add("Поле 'Фамилия Имя' обязательно для заполнения");
        }

        LocalDate birthDateObj = null;
        try {
            birthDateObj = LocalDate.parse(birthdate);
            if (birthDateObj.plusYears(18).isAfter(LocalDate.now())) {
                errors.add("Возраст должен быть старше 18 лет");
            }
        } catch (DateTimeParseException e) {
            errors.add("Неверный формат даты рождения (требуется YYYY-MM-DD)");
        }

        if (!errors.isEmpty()) {
            fillModelWithCurrentData(model, authentication);
            model.addAttribute("errors", errors);
            model.addAttribute("info", null);
            return "main";
        }

        try {
            AccountInfo updated = gatewayClient.updateAccount(authentication, name, birthDateObj);
            List<AccountDto> accounts = gatewayClient.getAccounts(authentication);
            fillModel(model, updated, accounts, Collections.emptyList(), "Данные успешно обновлены");
        } catch (Exception e) {
            fillModelWithCurrentData(model, authentication);
            model.addAttribute("errors", List.of("Ошибка обновления: " + e.getMessage()));
            model.addAttribute("info", null);
        }
        return "main";
    }

    @PostMapping("/cash")
    public String editCash(
            Model model,
            @RequestParam("value") int value,
            @RequestParam("action") CashAction action,
            Authentication authentication
    ) {
        List<String> errors = new ArrayList<>();
        if (value <= 0) {
            errors.add("Сумма должна быть положительной");
        }

        if (!errors.isEmpty()) {
            fillModelWithCurrentData(model, authentication);
            model.addAttribute("errors", errors);
            model.addAttribute("info", null);
            return "main";
        }

        try {
            AccountInfo updated = gatewayClient.cashAction(authentication, value, action);
            List<AccountDto> accounts = gatewayClient.getAccounts(authentication);
            String info = action == CashAction.PUT ? "Счёт пополнен" : "Снятие выполнено";
            fillModel(model, updated, accounts, Collections.emptyList(), info);
        } catch (Exception e) {
            fillModelWithCurrentData(model, authentication);
            model.addAttribute("errors", List.of("Ошибка операции: " + e.getMessage()));
            model.addAttribute("info", null);
        }
        return "main";
    }

    @PostMapping("/transfer")
    public String transfer(
            Model model,
            @RequestParam("value") int value,
            @RequestParam("login") String login,
            Authentication authentication
    ) {
        List<String> errors = new ArrayList<>();
        if (value <= 0) {
            errors.add("Сумма перевода должна быть положительной");
        }
        if (login == null || login.trim().isEmpty()) {
            errors.add("Не выбран аккаунт получателя");
        }

        if (!errors.isEmpty()) {
            fillModelWithCurrentData(model, authentication);
            model.addAttribute("errors", errors);
            model.addAttribute("info", null);
            return "main";
        }

        try {
            AccountInfo updated = gatewayClient.transfer(authentication, value, login);
            List<AccountDto> accounts = gatewayClient.getAccounts(authentication);
            fillModel(model, updated, accounts, Collections.emptyList(), "Перевод выполнен успешно");
        } catch (Exception e) {
            fillModelWithCurrentData(model, authentication);
            model.addAttribute("errors", List.of("Ошибка перевода: " + e.getMessage()));
            model.addAttribute("info", null);
        }
        return "main";
    }

    private void fillModel(Model model, AccountInfo info, List<AccountDto> accounts,
                           List<String> errors, String infoMessage) {
        model.addAttribute("name", info.getName());
        model.addAttribute("birthdate", info.getBirthdate());
        model.addAttribute("sum", info.getBalance());
        model.addAttribute("accounts", accounts != null ? accounts : List.of());
        model.addAttribute("errors", errors != null ? errors : Collections.emptyList());
        model.addAttribute("info", infoMessage);
    }

    private void fillModelWithCurrentData(Model model, Authentication authentication) {
        try {
            AccountInfo info = gatewayClient.getCurrentAccount(authentication);
            List<AccountDto> accounts = gatewayClient.getAccounts(authentication);
            fillModel(model, info, accounts, null, null);
        } catch (Exception e) {
            model.addAttribute("name", "");
            model.addAttribute("birthdate", "");
            model.addAttribute("sum", 0);
            model.addAttribute("accounts", List.of());
        }
    }
}
