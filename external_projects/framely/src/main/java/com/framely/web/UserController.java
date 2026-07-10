package com.framely.web;

import com.framely.account.Account;
import com.framely.transaction.TransactionService;
import com.framely.user.User;
import com.framely.user.UserService;
import com.framely.web.dto.AccountResponse;
import com.framely.web.dto.BalanceResponse;
import com.framely.web.dto.CreateAccountRequest;
import com.framely.web.dto.CreateUserRequest;
import com.framely.web.dto.SummaryResponse;
import com.framely.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final TransactionService transactionService;

    public UserController(UserService userService, TransactionService transactionService) {
        this.userService = userService;
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        User user = userService.createUser(req.name(), req.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return UserResponse.from(userService.getById(id));
    }

    @PostMapping("/{id}/accounts")
    public ResponseEntity<AccountResponse> addAccount(@PathVariable Long id,
                                                      @Valid @RequestBody CreateAccountRequest req) {
        Account account = userService.createAccount(id, req.name(), req.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        User user = userService.getById(id);
        BigDecimal total = transactionService.consolidatedBalance(user);
        List<AccountResponse> accounts = transactionService.accounts(user).stream()
                .map(AccountResponse::from)
                .toList();
        return new BalanceResponse(total, accounts);
    }

    @GetMapping("/{id}/summary")
    public SummaryResponse summary(@PathVariable Long id) {
        User user = userService.getById(id);
        return SummaryResponse.from(transactionService.monthlySummary(user));
    }
}
