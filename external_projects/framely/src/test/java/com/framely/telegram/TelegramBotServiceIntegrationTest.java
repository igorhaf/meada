package com.framely.telegram;

import com.framely.transaction.TransactionType;
import com.framely.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TelegramBotServiceIntegrationTest {

    /**
     * Substitui o stub por um extractor determinístico, exercitando o caminho completo
     * texto livre -> extractor (camada de IA) -> TransactionService -> confirmação.
     */
    @TestConfiguration
    static class FakeExtractorConfig {
        @Bean
        @Primary
        TransactionExtractor fakeExtractor() {
            return message -> new ExtractedTransaction(
                    TransactionType.SAIDA, new BigDecimal("50.00"), "mercado", "Alimentação", null);
        }
    }

    @Autowired
    TelegramBotService botService;

    @Autowired
    UserService userService;

    @Test
    void startShowsWelcome() {
        assertThat(botService.handle(1001L, "/start")).contains("Bem-vindo ao Framely");
    }

    @Test
    void freeTextFromUnlinkedChatAsksToLink() {
        assertThat(botService.handle(2002L, "gastei 50 no mercado"))
                .contains("não está vinculado");
    }

    @Test
    void vincularUnknownEmailFails() {
        assertThat(botService.handle(3003L, "/vincular ninguem@framely.test"))
                .contains("Não encontrei");
    }

    @Test
    void linkThenLaunchTransactionAndQuery() {
        String email = "bot" + System.nanoTime() + "@framely.test";
        userService.createUser("Bot User", email);
        Long chatId = 4004L;

        assertThat(botService.handle(chatId, "/vincular " + email)).contains("vinculado");

        String confirmation = botService.handle(chatId, "gastei 50 no mercado");
        assertThat(confirmation).contains("Saída").contains("Alimentação").contains("Novo saldo");

        assertThat(botService.handle(chatId, "/saldo")).contains("Saldo total");
        assertThat(botService.handle(chatId, "/resumo")).contains("Resumo");
    }
}
