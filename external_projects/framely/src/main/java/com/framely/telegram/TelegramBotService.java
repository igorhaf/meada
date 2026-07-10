package com.framely.telegram;

import com.framely.account.Account;
import com.framely.account.AccountType;
import com.framely.common.BusinessException;
import com.framely.common.Money;
import com.framely.common.NotFoundException;
import com.framely.transaction.MonthlySummary;
import com.framely.transaction.RecordTransactionCommand;
import com.framely.transaction.Transaction;
import com.framely.transaction.TransactionOrigin;
import com.framely.transaction.TransactionService;
import com.framely.transaction.TransactionType;
import com.framely.user.User;
import com.framely.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lógica do bot de Telegram, isolada da infraestrutura de polling (testável sem rede).
 *
 * <p>Comandos fixos ({@code /start}, {@code /vincular}, {@code /saldo}, {@code /resumo})
 * são tratados aqui diretamente. Texto livre é repassado ao {@link TransactionExtractor}
 * (camada de IA) e o resultado alimenta o {@link TransactionService} — a MESMA regra de
 * negócio da API REST. O bot é apenas outra porta de entrada.
 */
@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final String NOT_LINKED =
            "🔒 Seu Telegram ainda não está vinculado a uma conta.\n"
                    + "Use /vincular <email> para começar.";
    private static final String AI_NOT_READY =
            "🤖 A interpretação por IA ainda não está ativa.\n"
                    + "Em breve você poderá lançar transações escrevendo em linguagem natural "
                    + "(ex.: \"gastei 50 no mercado\").\n"
                    + "Por enquanto, use /saldo e /resumo.";

    private final UserService userService;
    private final TransactionService transactionService;
    private final TransactionExtractor extractor;

    public TelegramBotService(UserService userService, TransactionService transactionService,
                              TransactionExtractor extractor) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.extractor = extractor;
    }

    /**
     * Ponto de entrada: recebe chatId + texto e devolve a resposta a ser enviada.
     */
    public String handle(Long chatId, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        log.info("Telegram <- chatId={} text=\"{}\"", chatId, text);
        String reply = text.startsWith("/") ? handleCommand(chatId, text) : handleFreeText(chatId, text);
        log.info("Telegram -> chatId={} reply=\"{}\"", chatId, reply.replace("\n", " | "));
        return reply;
    }

    private String handleCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase(PT_BR);
        String args = parts.length > 1 ? parts[1].trim() : "";
        return switch (command) {
            case "/start" -> welcome();
            case "/vincular" -> vincular(chatId, args);
            case "/saldo" -> withUser(chatId, this::saldo);
            case "/resumo" -> withUser(chatId, this::resumo);
            default -> "Comando não reconhecido. Envie /start para ver as opções.";
        };
    }

    private String handleFreeText(Long chatId, String text) {
        Optional<User> user = userService.findByTelegramChatId(chatId);
        if (user.isEmpty()) {
            return NOT_LINKED;
        }
        if (text.isBlank()) {
            return "Envie uma mensagem descrevendo a transação, ou use /saldo e /resumo.";
        }

        ExtractedTransaction extracted;
        try {
            extracted = extractor.extract(text);
        } catch (UnsupportedOperationException ex) {
            // Camada de IA ainda não plugada (stub). Resposta amigável, sem quebrar o fluxo.
            return AI_NOT_READY;
        }

        try {
            Transaction transaction = transactionService.record(new RecordTransactionCommand(
                    user.get().getId(), extracted.type(), extracted.amount(), extracted.description(),
                    extracted.categoryName(), null, extracted.accountName(), TransactionOrigin.TELEGRAM));
            return confirmation(transaction);
        } catch (BusinessException | NotFoundException ex) {
            return "❌ " + ex.getMessage();
        }
    }

    private String welcome() {
        return "👋 Bem-vindo ao Framely, seu controle financeiro pessoal!\n\n"
                + "Para começar, vincule seu Telegram à sua conta:\n"
                + "• /vincular <email>\n\n"
                + "Depois, é só me mandar uma mensagem, por exemplo:\n"
                + "\"gastei 50 no mercado\" ou \"recebi 3000 de salário\".\n\n"
                + "Comandos disponíveis:\n"
                + "• /saldo — saldo total e por conta\n"
                + "• /resumo — receitas, despesas e saldo do mês";
    }

    private String vincular(Long chatId, String email) {
        if (email.isBlank()) {
            return "Uso: /vincular <email>";
        }
        try {
            User user = userService.linkTelegram(email, chatId);
            return "✅ Pronto! Este Telegram está vinculado a " + user.getEmail() + ".\n"
                    + "Agora você pode lançar transações e usar /saldo e /resumo.";
        } catch (NotFoundException ex) {
            return "❌ Não encontrei nenhum usuário com o email \"" + email + "\". Confira e tente novamente.";
        }
    }

    private String saldo(User user) {
        BigDecimal total = transactionService.consolidatedBalance(user);
        List<Account> accounts = transactionService.accounts(user);
        StringBuilder sb = new StringBuilder("💰 Saldo total: ").append(Money.brl(total));
        if (!accounts.isEmpty()) {
            sb.append("\n\nPor conta:");
            for (Account account : accounts) {
                sb.append("\n• ").append(account.getName())
                        .append(" (").append(accountTypeLabel(account.getType())).append("): ")
                        .append(Money.brl(account.getBalance()));
            }
        }
        return sb.toString();
    }

    private String resumo(User user) {
        MonthlySummary summary = transactionService.monthlySummary(user);
        String monthName = summary.month().getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        monthName = Character.toUpperCase(monthName.charAt(0)) + monthName.substring(1);
        return "📊 Resumo de " + monthName + "/" + summary.month().getYear() + "\n\n"
                + "Receitas: " + Money.brl(summary.receitas()) + "\n"
                + "Despesas: " + Money.brl(summary.despesas()) + "\n"
                + "Saldo do mês: " + Money.brl(summary.saldo());
    }

    private String confirmation(Transaction t) {
        String verbo = t.getType() == TransactionType.SAIDA ? "Saída" : "Entrada";
        String categoria = t.getCategory() == null ? "Outros" : t.getCategory().getName();
        return "✅ " + verbo + " de " + Money.brl(t.getAmount())
                + " em " + categoria + " (" + t.getAccount().getName() + ")."
                + " Novo saldo: " + Money.brl(t.getAccount().getBalance());
    }

    private String withUser(Long chatId, Function<User, String> action) {
        return userService.findByTelegramChatId(chatId).map(action).orElse(NOT_LINKED);
    }

    private String accountTypeLabel(AccountType type) {
        return switch (type) {
            case CORRENTE -> "Corrente";
            case DINHEIRO -> "Dinheiro";
            case POUPANCA -> "Poupança";
        };
    }
}
