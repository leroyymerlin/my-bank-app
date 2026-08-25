package ru.yandex.practicum;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.model.CashAction;

import static org.assertj.core.api.Assertions.assertThat;

class CashActionTest {

    @Test
    void enum_shouldHavePutAction() {
        CashAction action = CashAction.PUT;
        assertThat(action).isNotNull();
        assertThat(action.name()).isEqualTo("PUT");
    }

    @Test
    void enum_shouldHaveGetAction() {
        CashAction action = CashAction.GET;
        assertThat(action).isNotNull();
        assertThat(action.name()).isEqualTo("GET");
    }

    @Test
    void enum_shouldHaveExactlyTwoValues() {
        assertThat(CashAction.values())
                .hasSize(2)
                .contains(CashAction.PUT, CashAction.GET);
    }

    @Test
    void putAction_shouldMatchCashService() {
        assertThat(CashAction.PUT.name()).isEqualTo("PUT");
    }

    @Test
    void getAction_shouldMatchCashService() {
        assertThat(CashAction.GET.name()).isEqualTo("GET");
    }
}
