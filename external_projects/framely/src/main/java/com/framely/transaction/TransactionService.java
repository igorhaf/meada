package com.framely.transaction;

import com.framely.account.Account;
import com.framely.account.AccountRepository;
import com.framely.account.AccountType;
import com.framely.category.Category;
import com.framely.category.CategoryRepository;
import com.framely.common.BusinessException;
import com.framely.common.NotFoundException;
import com.framely.user.User;
import com.framely.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Regra de negócio de transações — ponto único compartilhado pela API REST e pelo bot.
 * O bot é apenas outra porta de entrada: monta um {@link RecordTransactionCommand} e chama
 * {@link #record}. Nada de lógica de persistência/saldo é duplicado fora daqui.
 */
@Service
public class TransactionService {

    private static final String DEFAULT_CATEGORY = "Outros";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Transaction record(RecordTransactionCommand cmd) {
        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        if (cmd.type() == null) {
            throw new BusinessException("Tipo da transação é obrigatório");
        }
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new BusinessException("Valor deve ser maior que zero");
        }

        Account account = resolveAccount(user, cmd.accountId(), cmd.accountName());
        Category category = resolveCategory(user, cmd.categoryName());

        String description = (cmd.description() == null || cmd.description().isBlank())
                ? defaultDescription(cmd.type())
                : cmd.description().trim();
        TransactionOrigin origin = cmd.origin() == null ? TransactionOrigin.API : cmd.origin();

        Transaction transaction = new Transaction(user, account, category, cmd.type(),
                cmd.amount(), description, origin, LocalDateTime.now());

        BigDecimal delta = cmd.type() == TransactionType.ENTRADA ? cmd.amount() : cmd.amount().negate();
        account.setBalance(account.getBalance().add(delta));
        accountRepository.save(account);

        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public BigDecimal consolidatedBalance(User user) {
        return accountRepository.findByUserOrderByIdAsc(user).stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<Account> accounts(User user) {
        return accountRepository.findByUserOrderByIdAsc(user);
    }

    @Transactional(readOnly = true)
    public MonthlySummary monthlySummary(User user) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        BigDecimal receitas = transactionRepository.sumByType(user, TransactionType.ENTRADA, start, end);
        BigDecimal despesas = transactionRepository.sumByType(user, TransactionType.SAIDA, start, end);
        return new MonthlySummary(YearMonth.from(today), receitas, despesas, receitas.subtract(despesas));
    }

    private Account resolveAccount(User user, Long accountId, String accountName) {
        if (accountId != null) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
            if (!account.getUser().getId().equals(user.getId())) {
                throw new BusinessException("Conta não pertence ao usuário");
            }
            return account;
        }
        List<Account> accounts = accountRepository.findByUserOrderByIdAsc(user);
        if (accountName != null && !accountName.isBlank()) {
            String wanted = accountName.trim();
            return accounts.stream()
                    .filter(a -> a.getName().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Conta \"" + wanted + "\" não encontrada"));
        }
        return defaultAccount(accounts);
    }

    private Account defaultAccount(List<Account> accounts) {
        if (accounts.isEmpty()) {
            throw new BusinessException("Usuário não possui contas cadastradas");
        }
        return accounts.stream().filter(a -> a.getType() == AccountType.CORRENTE).findFirst()
                .or(() -> accounts.stream().filter(a -> a.getType() == AccountType.DINHEIRO).findFirst())
                .orElse(accounts.get(0));
    }

    private Category resolveCategory(User user, String categoryName) {
        String name = (categoryName == null || categoryName.isBlank())
                ? DEFAULT_CATEGORY : categoryName.trim();
        return categoryRepository.findByUserAndNameIgnoreCase(user, name)
                .orElseGet(() -> categoryRepository.save(new Category(user, name)));
    }

    private String defaultDescription(TransactionType type) {
        return type == TransactionType.ENTRADA ? "Entrada" : "Saída";
    }
}
