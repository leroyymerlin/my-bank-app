package ru.yandex.practicum.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @DecimalMin(value = "0.01", message = "Сумма перевода должна быть больше нуля")
    private BigDecimal amount;
    private String toLogin;

}
