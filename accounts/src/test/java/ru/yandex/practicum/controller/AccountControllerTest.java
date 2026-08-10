package ru.yandex.practicum.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.exception.AccountNotFoundException;
import ru.yandex.practicum.service.AccountService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@ActiveProfiles("test")
@WithMockUser(username = "testuser")
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
    private static final int TEST_BALANCE = 1000;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("preferred_username", TEST_LOGIN)
                .claim("sub", TEST_LOGIN)
                .build();

        Authentication authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
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
                .andExpect(jsonPath("$.balance").value(TEST_BALANCE));
    }

    @Test
    void getCurrentAccount_whenAccountNotFound_shouldReturn404() throws Exception {
        when(accountService.getAccountInfo(TEST_LOGIN))
                .thenThrow(new AccountNotFoundException("Аккаунт не найден"));

        mockMvc.perform(get("/api/accounts/current")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Аккаунт не найден"));
    }
}