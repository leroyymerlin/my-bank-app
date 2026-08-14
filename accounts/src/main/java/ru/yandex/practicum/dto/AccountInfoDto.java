package ru.yandex.practicum.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfoDto {

    private String name;
    private String birthdate;

    @DecimalMin(value = "0.00", message = "Баланс не может быть отрицательным")
    private BigDecimal balance;
}
