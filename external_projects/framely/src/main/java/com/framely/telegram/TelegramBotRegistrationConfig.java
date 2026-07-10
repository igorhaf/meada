package com.framely.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Registra o bot por long polling — apenas quando um TELEGRAM_BOT_TOKEN está configurado.
 *
 * <p>O registro é feito manualmente (em vez de depender do auto-config do
 * telegrambots-spring-boot-starter, que ainda usa o mecanismo legado spring.factories),
 * garantindo funcionamento estável no Spring Boot 3. Sem token — em testes ou quando só a
 * API REST é usada — este {@code @Configuration} é ignorado e a aplicação sobe sem o bot.
 */
@Configuration
@ConditionalOnExpression("'${telegram.bot.token:}' != ''")
public class TelegramBotRegistrationConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrationConfig.class);

    @Bean
    public TelegramBotsApi telegramBotsApi(
            @Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username:}") String username,
            TelegramBotService botService) throws TelegramApiException {
        FramelyTelegramBot bot = new FramelyTelegramBot(token, username, botService);
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);
        log.info("Bot de Telegram registrado por long polling: @{}", username);
        return api;
    }
}
