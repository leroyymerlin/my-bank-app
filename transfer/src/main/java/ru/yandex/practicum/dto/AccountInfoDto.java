package ru.yandex.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfoDto {

    private String name;
    private String birthdate;
    private BigDecimal balance;
}
