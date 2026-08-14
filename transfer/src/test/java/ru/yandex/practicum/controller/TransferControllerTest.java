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
import ru.yandex.practicum.dto.TransferRequest;
import ru.yandex.practicum.service.TransferService;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransferService transferService;

    private static final String TEST_LOGIN = "testuser";

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void transfer_shouldReturn200_onValidTransfer() throws Exception {
        TransferRequest request = new TransferRequest(new BigDecimal("500.00"), "recipient");
        AccountInfoDto response = new AccountInfoDto("Иванов Иван", "1990-01-01", new BigDecimal("500.00"));

        when(transferService.transfer(TEST_LOGIN, "recipient", new BigDecimal("500.00")))
                .thenReturn(response);

        mockMvc.perform(post("/api/transfer")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    @WithMockUser(username = TEST_LOGIN)
    void transfer_shouldReturn400_onInvalidAmount() throws Exception {
        TransferRequest request = new TransferRequest(BigDecimal.ZERO, "recipient");

        mockMvc.perform(post("/api/transfer")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_shouldReturn401_whenNoJwt() throws Exception {
        TransferRequest request = new TransferRequest(new BigDecimal("100.00"), "recipient");

        mockMvc.perform(post("/api/transfer")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
