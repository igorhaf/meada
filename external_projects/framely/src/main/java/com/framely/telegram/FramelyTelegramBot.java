package com.framely.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Adaptador de infraestrutura do Telegram (long polling). Recebe os updates, delega toda
 * a lógica para {@link TelegramBotService} e envia a resposta. Sem regra de negócio aqui.
 */
public class FramelyTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(FramelyTelegramBot.class);

    private final String username;
    private final TelegramBotService botService;

    public FramelyTelegramBot(String token, String username, TelegramBotService botService) {
        super(token);
        this.username = username;
        this.botService = botService;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null || !update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        String reply;
        try {
            reply = botService.handle(chatId, text);
        } catch (Exception ex) {
            log.error("Erro ao processar mensagem do chatId {}", chatId, ex);
            reply = "⚠️ Ocorreu um erro ao processar sua mensagem. Tente novamente.";
        }

        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(reply)
                    .build());
        } catch (TelegramApiException ex) {
            log.error("Falha ao enviar resposta ao chatId {}", chatId, ex);
        }
    }
}
