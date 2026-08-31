package ru.yandex.practicum.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.config.TestSecurityConfig;
import ru.yandex.practicum.service.AccountService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class BalanceUpdateValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    private static final String TEST_LOGIN = "testuser";

    @Test
    @WithMockUser(authorities = "SCOPE_internal")
    void updateBalance_insufficientFunds_shouldReturn400() throws Exception {
        when(accountService.updateBalance(anyString(), any()))
                .thenThrow(new IllegalArgumentException("Недостаточно средств на счёте"));

        mockMvc.perform(post("/api/accounts/balance")
                        .with(csrf())
                        .param("login", TEST_LOGIN)
                        .param("delta", "-2000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Недостаточно средств на счёте"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_internal")
    void updateBalance_exceedsMaxLimit_shouldReturn400() throws Exception {
        when(accountService.updateBalance(anyString(), any()))
                .thenThrow(new IllegalArgumentException("Превышен максимальный баланс"));

        mockMvc.perform(post("/api/accounts/balance")
                        .with(csrf())
                        .param("login", TEST_LOGIN)
                        .param("delta", "2000000000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Превышен максимальный баланс"));
    }
}
