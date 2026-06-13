package com.pratham.paymentx.service.impl;

import com.cashfree.pg.Cashfree;
import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.CustomerDetails;
import com.cashfree.pg.model.OrderCreateRefundRequest;
import com.cashfree.pg.model.OrderEntity;
import com.pratham.paymentx.dto.transaction.topup.TopupInitiateRequest;
import com.pratham.paymentx.dto.transaction.topup.TopupInitiateResponse;
import com.pratham.paymentx.entity.StudentProfile;
import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.entity.Wallet;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.exception.ConflictException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.StudentProfileRepository;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.repository.WalletRepository;
import com.pratham.paymentx.service.NotificationService;
import com.pratham.paymentx.service.TopupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopupServiceImpl implements TopupService, ApplicationContextAware {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final Cashfree cashfree;
    private final NotificationService notificationService;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private TopupService self() {
        return applicationContext.getBean(TopupService.class);
    }

    @Override
    // NO @Transactional: Orchestrator holds no DB connections during HTTP calls.
    public TopupInitiateResponse initiateTopup(UUID userId, TopupInitiateRequest request) {

        // 1. FAST READ: Hardened Idempotency Check
        Optional<Transaction> existingTxOpt = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingTxOpt.isPresent()) {
            Transaction tx = existingTxOpt.get();

            // Defense 1: Cross-Domain Collision Prevention
            if (tx.getTransactionType() != TransactionType.TOPUP) {
                log.error("Security Alert: Idempotency key {} reused across domains by user {}.", request.getIdempotencyKey(), userId);
                throw new BadRequestException("Invalid or corrupted idempotency key.");
            }

            // Defense 2: Cryptographic Ownership Verification
            if (tx.getReceiverWallet() == null || !tx.getReceiverWallet().getUser().getId().equals(userId)) {
                log.warn("Security Alert: User {} attempted to hijack top-up idempotency key belonging to another user.", userId);
                throw new BadRequestException("Idempotency key collision detected. Please try again.");
            }

            if (tx.getTransactionStatus() != TransactionStatus.PENDING) {
                throw new BadRequestException("This top-up request has already been finalized.");
            }

            log.info("Idempotent request detected. Recovering top-up session for txId: {}", tx.getId());
            try {
                ApiResponse<OrderEntity> fetchResponse = cashfree.PGFetchOrder(tx.getId().toString(), null, null, null);
                return TopupInitiateResponse.builder()
                        .transactionId(tx.getId())
                        .paymentSessionId(fetchResponse.getData().getPaymentSessionId())
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Payment Gateway unavailable. Please try again later.", e);
            }
        }

        // 2. ISOLATED TRANSACTION: Pre-commit DB Record
        Transaction transaction;
        try {
            transaction = self().createPendingTopupRecord(userId, request);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent top-up attempt blocked for key: {}", request.getIdempotencyKey());
            throw new ConflictException("A request with this ID is currently processing.");
        }

        UUID transactionId = transaction.getId();

        // 3. NETWORK I/O: Call Cashfree
        try {
            StudentProfile profile = studentProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

            CustomerDetails customerDetails = new CustomerDetails();
            customerDetails.setCustomerId(userId.toString());
            customerDetails.setCustomerPhone(profile.getPhone());
            customerDetails.setCustomerName(profile.getFullName());

            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setOrderId(transactionId.toString());
            orderRequest.setOrderAmount(request.getAmount());
            orderRequest.setOrderCurrency("INR");
            orderRequest.setCustomerDetails(customerDetails);

            ApiResponse<OrderEntity> response = cashfree.PGCreateOrder(orderRequest, null, null, null);
            return TopupInitiateResponse.builder()
                    .transactionId(transactionId)
                    .paymentSessionId(response.getData().getPaymentSessionId())
                    .build();

        } catch (Exception e) {
            log.error("Failed to initiate Cashfree order for txId: {}. Leaving PENDING for fallback recovery.", transactionId, e);
            throw new RuntimeException("Payment Gateway unavailable. Please try again later.", e);
        }
    }

    @Override
    // NO @Transactional: Orchestrator logic only
    public void executeFulfillmentEngine(UUID transactionId) {
        // 1. FAST READ: Ensure it's still pending before bothering Cashfree
        Transaction preCheck = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (preCheck.getTransactionStatus() != TransactionStatus.PENDING) {
            return;
        }

        // 2. NETWORK I/O: Fetch actual status
        String gatewayStatus;
        try {
            ApiResponse<OrderEntity> response = cashfree.PGFetchOrder(transactionId.toString(), null, null, null);
            gatewayStatus = response.getData().getOrderStatus();
        } catch (com.cashfree.pg.ApiException e) {
            // FIX: Handle orders that dropped before reaching Cashfree
            if (e.getCode() == 404) {
                log.warn("Cashfree has no record of txId={}. Order creation failed previously. Marking FAILED locally.", transactionId);
                self().settleFailedTopup(transactionId);
                return; // Gracefully exit
            }
            log.error("Fulfillment Engine encountered Cashfree API error for txId: {}", transactionId, e);
            throw new RuntimeException("Upstream gateway error: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Fulfillment Engine failed due to network/unknown error for txId: {}", transactionId, e);
            throw new RuntimeException("Upstream gateway error", e);
        }

        // 3. STATE ROUTER -> Trigger Isolated DB Locks
        switch (gatewayStatus != null ? gatewayStatus.toUpperCase() : "UNKNOWN") {
            case "PAID" -> {
                boolean ghostWalletDetected = self().settlePaidTopup(transactionId);

                // 4. POST-LOCK NETWORK I/O: If wallet was missing, trigger refund cleanly
                if (ghostWalletDetected) {
                    executeGhostWalletRefund(transactionId, preCheck.getAmount());
                }
            }
            case "EXPIRED", "TERMINATED" -> self().settleFailedTopup(transactionId);
            case "ACTIVE" -> log.info("Order txId={} is still ACTIVE. Awaiting completion.", transactionId);
        }
    }

    // Isolated Transactional Boundaries

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createPendingTopupRecord(UUID userId, TopupInitiateRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        Transaction transaction = Transaction.builder()
                .receiverWallet(wallet)
                .senderWallet(null) // Top-ups do not have a sender wallet
                .amount(request.getAmount())
                .transactionType(TransactionType.TOPUP)
                .transactionStatus(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        return transactionRepository.saveAndFlush(transaction);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settlePaidTopup(UUID transactionId) {
        // 1. Lock the transaction row
        Transaction tx = transactionRepository.findByIdAndLock(transactionId).orElseThrow();

        // Double check status under lock to ensure Webhook/Cron didn't race each other
        if (tx.getTransactionStatus() != TransactionStatus.PENDING) {
            return false;
        }

        // 2. Ghost Wallet Defense Part 1: Was the relation completely severed/nulled?
        if (tx.getReceiverWallet() == null) {
            log.error("CRITICAL: Receiver wallet relation is NULL for txId={}. Failing transaction locally.", transactionId);
            tx.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            return true; // Signal to orchestrator that a network refund is needed
        }

        // 3. Lock the wallet row (Strict ordering: Tx -> Wallet)
        Optional<Wallet> walletOpt = walletRepository.findByIdAndLock(tx.getReceiverWallet().getId());

        // Ghost Wallet Defense Part 2: Does the ID exist, but the wallet row is gone?
        if (walletOpt.isEmpty()) {
            log.error("CRITICAL: Receiver wallet row missing for txId={}. Failing transaction locally.", transactionId);
            tx.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            return true; // Signal to orchestrator that a network refund is needed
        }

        // 4. Ledger Update
        Wallet receiverWallet = walletOpt.get();
        receiverWallet.setAvailableBalance(receiverWallet.getAvailableBalance().add(tx.getAmount()));
        tx.setTransactionStatus(TransactionStatus.SUCCESS);

        walletRepository.save(receiverWallet);
        transactionRepository.save(tx);
        log.info("SUCCESS: Fulfilled top-up for txId={}. Credited ₹{}", transactionId, tx.getAmount());

        // 5. Send Email
        User user = receiverWallet.getUser();
        studentProfileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            try {
                notificationService.sendTopupSuccess(user.getEmail(), profile.getFullName(), tx.getAmount());
            } catch (Exception e) {
                // Enterprise Defense: Never let an email failure rollback a successful financial ledger update
                log.error("Ledger updated, but failed to queue Topup Success email for user: {}", user.getId(), e);
            }
        });

        return false; // No refund needed
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleFailedTopup(UUID transactionId) {
        Transaction tx = transactionRepository.findByIdAndLock(transactionId).orElseThrow();
        if (tx.getTransactionStatus() == TransactionStatus.PENDING) {
            tx.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            log.info("FAILED: Top-up marked as failed. txId={} expired or dropped.", transactionId);
        }
    }

    @Override
    @Transactional
    public int markAsFailedIfOlderThan(TransactionType type, TransactionStatus pendingStatus, TransactionStatus failedStatus, OffsetDateTime cutoffTime) {
        return transactionRepository.markAsFailedIfOlderThan(type, pendingStatus, failedStatus, cutoffTime);
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyTransactionOwnership(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!transaction.getReceiverWallet().getUser().getId().equals(userId)) {
            log.warn("Security Alert: User {} attempted to verify tx {} belonging to someone else.", userId, transactionId);
            throw new AccessDeniedException("You do not have permission to verify this transaction.");
        }
    }

    // Helper Methods

    private void executeGhostWalletRefund(UUID transactionId, BigDecimal amount) {
        log.warn("Initiating automatic gateway refund for Ghost Wallet txId={}", transactionId);
        try {
            OrderCreateRefundRequest refundRequest = new OrderCreateRefundRequest();
            refundRequest.setRefundAmount(amount);
            refundRequest.setRefundId("REF_" + transactionId.toString().replace("-", "").substring(0, 20));
            refundRequest.setRefundNote("User wallet missing prior to fulfillment.");
            refundRequest.setRefundSpeed(OrderCreateRefundRequest.RefundSpeedEnum.INSTANT);

            cashfree.PGOrderCreateRefund(transactionId.toString(), refundRequest, null, null, null);
            log.info("Successfully initiated automatic refund for orphaned txId={}", transactionId);

        } catch (Exception e) {
            log.error("URGENT: Failed to process refund for orphaned txId={}. Retrying...", transactionId, e);
            throw new RuntimeException("Auto-Refund failed. Retrying in background worker.", e);
        }
    }
}