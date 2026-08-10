package ru.yandex.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.yandex.practicum.model.CashAction;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CashRequest {

    private int amount;
    private CashAction action;
}
