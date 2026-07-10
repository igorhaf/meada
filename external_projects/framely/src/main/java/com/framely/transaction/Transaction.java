package com.framely.transaction;

import com.framely.account.Account;
import com.framely.category.Category;
import com.framely.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionOrigin origin;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected Transaction() {
    }

    public Transaction(User user, Account account, Category category, TransactionType type,
                       BigDecimal amount, String description, TransactionOrigin origin,
                       LocalDateTime occurredAt) {
        this.user = user;
        this.account = account;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.origin = origin;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public Category getCategory() {
        return category;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public TransactionOrigin getOrigin() {
        return origin;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
