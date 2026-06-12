package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @EntityGraph(attributePaths = {"receiverWallet", "receiverWallet.user"})
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdAndLock(@Param("id") UUID id);

    @Query("""
        SELECT t.id FROM Transaction t
        WHERE t.transactionType = :txType
        AND t.transactionStatus = :txStatus
        AND t.createdAt < :endTime
        AND t.createdAt > :startTime
    """)
    List<UUID> findStalePendingTopupIds(
            @Param("txType") TransactionType txType,
            @Param("txStatus") TransactionStatus txStatus,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Modifying
    @Query("UPDATE Transaction t SET t.transactionStatus = :failedStatus, t.updatedAt = CURRENT_TIMESTAMP WHERE t.transactionType = :type AND t.transactionStatus = :pendingStatus AND t.createdAt < :cutoffTime")
    int markAsFailedIfOlderThan(
            @Param("type") TransactionType type,
            @Param("pendingStatus") TransactionStatus pendingStatus,
            @Param("failedStatus") TransactionStatus failedStatus,
            @Param("cutoffTime") OffsetDateTime cutoffTime
    );
}