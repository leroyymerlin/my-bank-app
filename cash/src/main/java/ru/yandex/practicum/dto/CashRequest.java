package ru.yandex.practicum.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.yandex.practicum.model.CashAction;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CashRequest {

    @DecimalMin(value = "0.01", message = "Сумма должна быть больше нуля")
    private BigDecimal amount;
    private CashAction action;
}
