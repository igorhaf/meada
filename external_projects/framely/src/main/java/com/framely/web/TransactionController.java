package com.framely.web;

import com.framely.transaction.RecordTransactionCommand;
import com.framely.transaction.Transaction;
import com.framely.transaction.TransactionOrigin;
import com.framely.transaction.TransactionService;
import com.framely.web.dto.CreateTransactionRequest;
import com.framely.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest req) {
        RecordTransactionCommand cmd = new RecordTransactionCommand(
                req.userId(), req.type(), req.amount(), req.description(),
                req.categoryName(), req.accountId(), null, TransactionOrigin.API);
        Transaction transaction = transactionService.record(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }
}
