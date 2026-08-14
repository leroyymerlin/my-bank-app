package ru.yandex.practicum.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.exception.AccountNotFoundException;
import ru.yandex.practicum.service.AccountService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@ActiveProfiles("test")
@Import(ru.yandex.practicum.config.TestSecurityConfig.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    private static final String TEST_LOGIN = "testuser";
    private static final String TEST_NAME = "Иванов Иван";
    private static final LocalDate TEST_BIRTHDATE = LocalDate.of(1990, 1, 1);
    private static final BigDecimal TEST_BALANCE = new BigDecimal("1000.00");

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor createJwtRequestPostProcessor() {
        return (org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor) jwt();
    }

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void getCurrentAccount_shouldReturn200AndAccountInfo() throws Exception {
        AccountInfoDto expected = new AccountInfoDto(
                TEST_NAME,
                TEST_BIRTHDATE.format(DateTimeFormatter.ISO_DATE),
                TEST_BALANCE
        );
        when(accountService.getAccountInfo(TEST_LOGIN)).thenReturn(expected);

        mockMvc.perform(get("/api/accounts/current")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TEST_NAME))
                .andExpect(jsonPath("$.birthdate").value(TEST_BIRTHDATE.format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.balance").value(1000));
    }

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void getCurrentAccount_whenAccountNotFound_shouldReturn404() throws Exception {
        when(accountService.getAccountInfo(TEST_LOGIN))
                .thenThrow(new AccountNotFoundException("Аккаунт не найден"));

        mockMvc.perform(get("/api/accounts/current")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Аккаунт не найден"));
    }

    @Test
    void getCurrentAccount_shouldFailOnNoJwt() throws Exception {
        mockMvc.perform(get("/api/accounts/current")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());
    }
}