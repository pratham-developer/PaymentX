package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.projection.TransactionStats;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // generic method with Pageable to prevent OOM
    @Query("""
        SELECT t.id FROM Transaction t
        WHERE t.transactionType = :txType
        AND t.transactionStatus = :txStatus
        AND t.createdAt < :endTime
        AND t.createdAt > :startTime
        ORDER BY t.createdAt ASC
    """)
    List<UUID> findStaleTransactionIds(
            @Param("txType") TransactionType txType,
            @Param("txStatus") TransactionStatus txStatus,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Transaction t SET t.transactionStatus = :failedStatus, t.updatedAt = CURRENT_TIMESTAMP WHERE t.transactionType = :type AND t.transactionStatus = :pendingStatus AND t.createdAt < :cutoffTime")
    int markAsFailedIfOlderThan(
            @Param("type") TransactionType type,
            @Param("pendingStatus") TransactionStatus pendingStatus,
            @Param("failedStatus") TransactionStatus failedStatus,
            @Param("cutoffTime") OffsetDateTime cutoffTime
    );

    Optional<Transaction> findByOriginalTransaction(Transaction originalTransaction);

    @Query("SELECT t FROM Transaction t WHERE t.senderWallet.id = :walletId OR t.receiverWallet.id = :walletId ORDER BY t.createdAt DESC")
    List<Transaction> findRecentTransactionsByWalletId(@Param("walletId") UUID walletId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.senderWallet.id = :walletId OR t.receiverWallet.id = :walletId ORDER BY t.createdAt DESC")
    Page<Transaction> findAllTransactionsByWalletId(@Param("walletId") UUID walletId, Pageable pageable);

    @Query("SELECT COUNT(t) as txCount, COALESCE(SUM(t.amount), 0) as totalVolume " +
            "FROM Transaction t " +
            "WHERE t.transactionType = :type AND t.transactionStatus = :status " +
            "AND t.createdAt >= :startTime AND t.createdAt < :endTime")
    TransactionStats getTransactionStats(
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("startTime") java.time.OffsetDateTime startTime,
            @Param("endTime") java.time.OffsetDateTime endTime
    );
}