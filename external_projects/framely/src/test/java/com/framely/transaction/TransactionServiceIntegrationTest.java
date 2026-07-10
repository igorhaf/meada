package com.framely.transaction;

import com.framely.common.NotFoundException;
import com.framely.user.User;
import com.framely.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransactionServiceIntegrationTest {

    @Autowired
    UserService userService;

    @Autowired
    TransactionService transactionService;

    @Test
    void recordMovesBalanceAndAppliesDefaults() {
        User user = userService.createUser("Igor", uniqueEmail());

        Transaction saida = transactionService.record(new RecordTransactionCommand(
                user.getId(), TransactionType.SAIDA, new BigDecimal("50.00"),
                "mercado", null, null, null, TransactionOrigin.TELEGRAM));

        // origem, categoria padrão "Outros" e conta padrão "Carteira" resolvidas
        assertThat(saida.getOrigin()).isEqualTo(TransactionOrigin.TELEGRAM);
        assertThat(saida.getCategory().getName()).isEqualTo("Outros");
        assertThat(saida.getAccount().getName()).isEqualTo("Carteira");
        assertThat(saida.getAccount().getBalance()).isEqualByComparingTo("-50.00");

        transactionService.record(new RecordTransactionCommand(
                user.getId(), TransactionType.ENTRADA, new BigDecimal("100.00"),
                "salário", "Salário", null, null, TransactionOrigin.API));

        assertThat(transactionService.consolidatedBalance(user)).isEqualByComparingTo("50.00");

        MonthlySummary summary = transactionService.monthlySummary(user);
        assertThat(summary.receitas()).isEqualByComparingTo("100.00");
        assertThat(summary.despesas()).isEqualByComparingTo("50.00");
        assertThat(summary.saldo()).isEqualByComparingTo("50.00");
    }

    @Test
    void unknownAccountNameFails() {
        User user = userService.createUser("Ana", uniqueEmail());
        assertThatThrownBy(() -> transactionService.record(new RecordTransactionCommand(
                user.getId(), TransactionType.SAIDA, new BigDecimal("10.00"),
                "x", null, null, "Inexistente", TransactionOrigin.TELEGRAM)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        User user = userService.createUser("Zé", uniqueEmail());
        assertThatThrownBy(() -> transactionService.record(new RecordTransactionCommand(
                user.getId(), TransactionType.ENTRADA, new BigDecimal("0"),
                "x", null, null, null, TransactionOrigin.API)))
                .isInstanceOf(RuntimeException.class);
    }

    private String uniqueEmail() {
        return "u" + System.nanoTime() + "@framely.test";
    }
}
