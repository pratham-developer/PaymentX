package com.pratham.paymentx.service.impl;

import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.CustomerDetails;
import com.cashfree.pg.model.OrderEntity;
import com.pratham.paymentx.dto.transaction.TopupInitiateRequest;
import com.pratham.paymentx.dto.transaction.TopupInitiateResponse;
import com.pratham.paymentx.entity.StudentProfile;
import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.entity.Wallet;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.StudentProfileRepository;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.repository.WalletRepository;
import com.pratham.paymentx.service.TopupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopupServiceImpl implements TopupService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final Cashfree cashfree;

    @Override
    @Transactional
    public TopupInitiateResponse initiateTopup(UUID userId, TopupInitiateRequest request) {

        // 1. TRUE IDEMPOTENCY CHECK
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingTx.isPresent()) {
            Transaction tx = existingTx.get();
            log.info("Idempotent request detected. Recovering top-up session for txId: {}", tx.getId());

            try {
                ApiResponse<OrderEntity> fetchResponse = cashfree.PGFetchOrder(tx.getId().toString(), null, null, null);
                return TopupInitiateResponse.builder()
                        .transactionId(tx.getId())
                        .paymentSessionId(fetchResponse.getData().getPaymentSessionId())
                        .build();
            } catch (Exception e) {
                log.error("Failed to fetch existing order from Cashfree for txId: {}", tx.getId(), e);
                throw new RuntimeException("Payment Gateway unavailable. Please try again later.", e);
            }
        }

        log.info("Initiating new topup for userId: {} with amount: {}", userId, request.getAmount());

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

        // 2. CREATE PENDING INTENT
        Transaction transaction = Transaction.builder()
                // Do NOT set .id() manually. Let Hibernate generate it!
                .receiverWallet(wallet)
                .senderWallet(null) // External funding source
                .amount(request.getAmount())
                .transactionType(TransactionType.TOPUP)
                .transactionStatus(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        try {
            // Save it first so Hibernate generates and populates the UUID
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate topup initiation blocked for idempotencyKey: {}", request.getIdempotencyKey());
            throw new IllegalStateException("A request with this ID is currently processing. Please refresh.");
        }

        // Extract the freshly generated DB ID to use as the Cashfree Order ID
        UUID transactionId = transaction.getId();

        // 3. BUILD CASHFREE ORDER
        CustomerDetails customerDetails = new CustomerDetails();
        customerDetails.setCustomerId(userId.toString());
        customerDetails.setCustomerPhone(profile.getPhone());
        customerDetails.setCustomerName(profile.getFullName());

        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setOrderId(transactionId.toString());
        orderRequest.setOrderAmount(request.getAmount());
        orderRequest.setOrderCurrency("INR");
        orderRequest.setCustomerDetails(customerDetails);

        try {
            // 4. INITIATE GATEWAY SESSION
            ApiResponse<OrderEntity> response = cashfree.PGCreateOrder(orderRequest, null, null, null);

            return TopupInitiateResponse.builder()
                    .transactionId(transactionId)
                    .paymentSessionId(response.getData().getPaymentSessionId())
                    .build();

        } catch (Exception e) {
            log.error("Failed to initiate Cashfree order for txId: {}", transactionId, e);
            throw new RuntimeException("Payment Gateway unavailable. Please try again later.", e);
        }
    }

    @Override
    @Transactional
    public void executeFulfillmentEngine(UUID transactionId) {
        log.info("Fulfillment Engine triggered for txId: {}", transactionId);

        // 1. LOCK #1: TRANSACTION (GATEKEEPER)
        Transaction transaction = transactionRepository.findByIdAndLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        // 2. IDEMPOTENCY EXIT
        if (transaction.getTransactionStatus() != TransactionStatus.PENDING) {
            log.info("Topup already processed for txId: {}. Current Status: {}",
                    transactionId, transaction.getTransactionStatus());
            return;
        }

        try {
            // 3. GATEWAY VERIFICATION (SOURCE OF TRUTH)
            ApiResponse<OrderEntity> response = cashfree.PGFetchOrder(transactionId.toString(), null, null, null);
            String gatewayStatus = response.getData().getOrderStatus();

            log.info("Gateway status for txId: {} is {}", transactionId, gatewayStatus);

            switch (gatewayStatus != null ? gatewayStatus.toUpperCase() : "UNKNOWN") {
                case "PAID" -> {
                    // 4. LOCK #2: WALLET (DEADLOCK PREVENTION - Always ordered after Transaction)
                    Wallet receiverWallet = walletRepository.findByIdAndLock(transaction.getReceiverWallet().getId())
                            .orElseThrow(() -> new IllegalStateException("Receiver wallet disappeared"));

                    // Credit the account
                    receiverWallet.setAvailableBalance(receiverWallet.getAvailableBalance().add(transaction.getAmount()));
                    transaction.setTransactionStatus(TransactionStatus.SUCCESS);

                    walletRepository.save(receiverWallet);
                    transactionRepository.save(transaction);

                    log.info("SUCCESS: Fulfilled top-up for txId={}. Credited ₹{}", transactionId, transaction.getAmount());
                }
                case "EXPIRED", "TERMINATED" -> {
                    transaction.setTransactionStatus(TransactionStatus.FAILED);
                    transactionRepository.save(transaction);
                    log.warn("FAILED: Top-up marked as terminal. txId={} expired at gateway.", transactionId);
                }
                case "ACTIVE" -> log.info("PENDING: Top-up txId={} is still awaiting customer action.", transactionId);
                default -> log.warn("UNKNOWN: Unhandled gateway status '{}' for txId={}", gatewayStatus, transactionId);
            }
        } catch (Exception e) {
            log.error("Fulfillment Engine failed to fetch Cashfree order status for txId: {}", transactionId, e);
            throw new RuntimeException("Upstream gateway error during fulfillment check", e);
        }
    }
}