package com.framely.user;

import com.framely.account.Account;
import com.framely.account.AccountRepository;
import com.framely.account.AccountType;
import com.framely.common.BusinessException;
import com.framely.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public UserService(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Cria o usuário e já provisiona uma conta padrão "Carteira" (DINHEIRO),
     * de modo que o bot sempre tenha uma conta default para lançar transações.
     */
    @Transactional
    public User createUser(String name, String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new BusinessException("Já existe um usuário com o email " + email);
        });
        User user = userRepository.save(new User(name, email));
        accountRepository.save(new Account(user, "Carteira", AccountType.DINHEIRO));
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramChatId(Long chatId) {
        return userRepository.findByTelegramChatId(chatId);
    }

    /**
     * Vincula o chatId do Telegram ao usuário com o email dado. Se o chatId já
     * estava vinculado a outro usuário, desvincula do anterior (unicidade do chatId).
     */
    @Transactional
    public User linkTelegram(String email, Long chatId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Nenhum usuário com o email " + email));
        userRepository.findByTelegramChatId(chatId).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                other.setTelegramChatId(null);
                userRepository.save(other);
            }
        });
        user.setTelegramChatId(chatId);
        return userRepository.save(user);
    }

    @Transactional
    public Account createAccount(Long userId, String name, AccountType type) {
        User user = getById(userId);
        return accountRepository.save(new Account(user, name, type));
    }

    @Transactional(readOnly = true)
    public List<Account> accounts(User user) {
        return accountRepository.findByUserOrderByIdAsc(user);
    }
}
