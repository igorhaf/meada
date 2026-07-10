package com.framely.transaction;

import com.framely.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select coalesce(sum(t.amount), 0) from Transaction t "
            + "where t.user = :user and t.type = :type "
            + "and t.occurredAt >= :start and t.occurredAt < :end")
    BigDecimal sumByType(@Param("user") User user,
                         @Param("type") TransactionType type,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);
}
