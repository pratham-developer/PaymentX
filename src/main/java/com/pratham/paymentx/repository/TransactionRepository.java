package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.projection.MerchantFlowStats;
import com.pratham.paymentx.projection.TransactionStats;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN t.receiverWallet.id = :walletId AND t.transactionType = 'PURCHASE' THEN t.amount ELSE 0 END), 0) as inflows, " +
            "COALESCE(SUM(CASE WHEN t.senderWallet.id = :walletId AND t.transactionType = 'PAYOUT' THEN t.amount ELSE 0 END), 0) as outflows, " +
            "COALESCE(SUM(CASE WHEN t.senderWallet.id = :walletId AND t.transactionType = 'FEE' THEN t.amount ELSE 0 END), 0) as fees " +
            "FROM Transaction t " +
            "WHERE (t.receiverWallet.id = :walletId OR t.senderWallet.id = :walletId) " +
            "AND t.transactionStatus = 'SUCCESS' " +
            "AND t.createdAt >= :startTime AND t.createdAt < :endTime")
    MerchantFlowStats getMerchantFlowsForPeriod(
            @Param("walletId") UUID walletId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // Ledger Math: (All Credits up to T) - (All Debits up to T)
    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN t.receiverWallet.id = :walletId THEN t.amount ELSE 0 END), 0) - " +
            "COALESCE(SUM(CASE WHEN t.senderWallet.id = :walletId THEN t.amount ELSE 0 END), 0) " +
            "FROM Transaction t " +
            "WHERE (t.receiverWallet.id = :walletId OR t.senderWallet.id = :walletId) " +
            "AND t.transactionStatus = 'SUCCESS' " +
            "AND t.createdAt < :timestamp")
    BigDecimal getHistoricalBalanceAt(
            @Param("walletId") UUID walletId,
            @Param("timestamp") OffsetDateTime timestamp
    );
}