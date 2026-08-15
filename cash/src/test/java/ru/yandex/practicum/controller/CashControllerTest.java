package ru.yandex.practicum.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.dto.AccountInfoDto;
import ru.yandex.practicum.dto.CashRequest;
import ru.yandex.practicum.model.CashAction;
import ru.yandex.practicum.service.CashService;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CashControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CashService cashService;

    private static final String TEST_LOGIN = "testuser";

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void processCash_shouldReturn200_onValidPut() throws Exception {
        CashRequest request = new CashRequest(new BigDecimal("1000.00"), CashAction.PUT);
        AccountInfoDto response = new AccountInfoDto("Иванов Иван", "1990-01-01", new BigDecimal("2000.00"));

        when(cashService.processCash(TEST_LOGIN, new BigDecimal("1000.00"), CashAction.PUT))
                .thenReturn(response);

        mockMvc.perform(post("/api/cash")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Иванов Иван"))
                .andExpect(jsonPath("$.balance").value(2000.00));
    }

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void processCash_shouldReturn200_onValidGet() throws Exception {
        CashRequest request = new CashRequest(new BigDecimal("500.00"), CashAction.GET);
        AccountInfoDto response = new AccountInfoDto("Иванов Иван", "1990-01-01", new BigDecimal("500.00"));

        when(cashService.processCash(TEST_LOGIN, new BigDecimal("500.00"), CashAction.GET))
                .thenReturn(response);

        mockMvc.perform(post("/api/cash")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void processCash_shouldReturn400_onInvalidAmount() throws Exception {
        CashRequest request = new CashRequest(BigDecimal.ZERO, CashAction.PUT);

        mockMvc.perform(post("/api/cash")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processCash_shouldReturn401_whenNoJwt() throws Exception {
        CashRequest request = new CashRequest(new BigDecimal("100.00"), CashAction.PUT);

        mockMvc.perform(post("/api/cash")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
