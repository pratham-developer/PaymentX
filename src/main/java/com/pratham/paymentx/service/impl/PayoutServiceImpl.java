package com.pratham.paymentx.service.impl;

import com.cashfree.CashfreePayout;
import com.cashfree.ApiResponse;
import com.cashfree.model.CreateTransferRequest;
import com.cashfree.model.CreateTransferResponse;
import com.cashfree.model.CreateTransferRequestBeneficiaryDetails;
import com.cashfree.model.CreateBatchTransferRequest;
import com.cashfree.model.CreateBatchTransferRequestTransfersInner;
import com.cashfree.model.CreateBatchTransferRequestTransfersInnerBeneficiaryDetails;
import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.entity.Wallet;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.MerchantProfileRepository;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.repository.WalletRepository;
import com.pratham.paymentx.service.NotificationService;
import com.pratham.paymentx.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutServiceImpl implements PayoutService, ApplicationContextAware {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CashfreePayout cashfreePayout;
    private final NotificationService notificationService;

    private ApplicationContext applicationContext;
    private static final String API_VERSION = "2024-01-01";
    private static final BigDecimal INSTANT_FEE = new BigDecimal("10.00");
    private static final int BATCH_SIZE_LIMIT = 100;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private PayoutService self() { return applicationContext.getBean(PayoutService.class); }

    // 1. INSTANT PAYOUT (Manual Self-Service)

    @Override
    public void initiateInstantPayout(UUID merchantUserId, BigDecimal amount) {

        MerchantProfile merchant = merchantProfileRepository.findByUserIdWithUser(merchantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        if (merchant.getGatewayStatus() != MerchantGatewayStatus.BENEFICIARY_CREATED || merchant.getCashfreeBeneficiaryId() == null) {
            throw new BadRequestException("Merchant account is pending banking approval.");
        }

        UUID idempotencyKey = UUID.randomUUID();
        self().quarantineInstantPayoutFunds(merchantUserId, amount, idempotencyKey);

        try {
            CreateTransferRequest request = new CreateTransferRequest();
            request.setTransferId(idempotencyKey.toString());
            request.setTransferAmount(amount.doubleValue());
            request.setTransferCurrency("INR");
            request.setTransferMode(CreateTransferRequest.TransferModeEnum.IMPS);

            CreateTransferRequestBeneficiaryDetails beneficiaryDetails = new CreateTransferRequestBeneficiaryDetails();
            beneficiaryDetails.setBeneficiaryId(merchant.getCashfreeBeneficiaryId());
            request.setBeneficiaryDetails(beneficiaryDetails);

            cashfreePayout.PayoutInitiateTransfer(API_VERSION, null, request, null);
            log.info("Instant Payout pushed to Cashfree. txId: {}", idempotencyKey);

        } catch (com.cashfree.ApiException e) {
            if (e.getCode() >= 400 && e.getCode() < 500) {
                log.error("Cashfree rejected payout txId: {}. Executing auto-refund.", idempotencyKey);
                self().refundFailedPayout(idempotencyKey);
                throw new BadRequestException("Payout rejected by banking partner.");
            }
            log.warn("Network timeout during instant payout txId: {}. Letting MQ heal it.", idempotencyKey);
        }
    }

    // 2. BATCH PAYOUT (Nightly Cron Driven)

    @Override
    public void processNightlyBatchSettlements() {
        List<MerchantProfile> eligibleMerchants = merchantProfileRepository.findEligibleMerchantsForAutoPayout();
        if (eligibleMerchants.isEmpty()) return;

        List<CreateBatchTransferRequestTransfersInner> currentBatch = new ArrayList<>();
        List<UUID> quarantinedTxIds = new ArrayList<>();
        String batchTransferId = "BATCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        for (MerchantProfile merchant : eligibleMerchants) {
            UUID idempotencyKey = UUID.randomUUID();

            Optional<Transaction> txOpt = self().quarantineFullBalanceForStandardPayout(merchant.getUser().getId(), idempotencyKey);

            if (txOpt.isPresent()) {
                Transaction tx = txOpt.get();
                quarantinedTxIds.add(tx.getId());

                CreateBatchTransferRequestTransfersInner transfer = new CreateBatchTransferRequestTransfersInner();
                transfer.setTransferId(idempotencyKey.toString());
                transfer.setTransferAmount(tx.getAmount().doubleValue());
                transfer.setTransferCurrency("INR");
                transfer.setTransferMode(CreateBatchTransferRequestTransfersInner.TransferModeEnum.NEFT);

                CreateBatchTransferRequestTransfersInnerBeneficiaryDetails beneficiaryDetails = new CreateBatchTransferRequestTransfersInnerBeneficiaryDetails();
                beneficiaryDetails.setBeneficiaryId(merchant.getCashfreeBeneficiaryId());
                transfer.setBeneficiaryDetails(beneficiaryDetails);

                currentBatch.add(transfer);

                if (currentBatch.size() == BATCH_SIZE_LIMIT) {
                    dispatchBatchToCashfree(batchTransferId, currentBatch, quarantinedTxIds);
                    currentBatch.clear();
                    quarantinedTxIds.clear();
                    batchTransferId = "BATCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
                }
            }
        }

        if (!currentBatch.isEmpty()) {
            dispatchBatchToCashfree(batchTransferId, currentBatch, quarantinedTxIds);
        }
    }

    private void dispatchBatchToCashfree(String batchId, List<CreateBatchTransferRequestTransfersInner> transfers, List<UUID> quarantinedTxIds) {
        CreateBatchTransferRequest batchRequest = new CreateBatchTransferRequest();
        batchRequest.setBatchTransferId(batchId);
        batchRequest.setTransfers(transfers);

        try {
            cashfreePayout.PayoutInitiateBatchTransfer(API_VERSION, null, batchRequest, null);
            log.info("Dispatched batch {} with {} transfers.", batchId, transfers.size());
        } catch (com.cashfree.ApiException e) {
            log.error("Batch rejection. Reverting {} quarantined transactions.", quarantinedTxIds.size());
            for (UUID txId : quarantinedTxIds) {
                try {
                    self().refundFailedPayout(txId);
                } catch (Exception ex) {
                    log.error("Failed to auto-refund txId {} after batch failure.", txId, ex);
                }
            }
        }
    }

    // 3. RECONCILIATION ROUTER

    @Override
    public void processPayoutReconciliation(UUID transactionId) {
        Transaction preCheck = transactionRepository.findById(transactionId).orElseThrow();
        if (preCheck.getTransactionStatus() != TransactionStatus.PENDING) return;

        String gatewayStatus;
        try {
            // FIX: Query Cashfree using the idempotencyKey, NOT the internal ID!
            ApiResponse<CreateTransferResponse> response = cashfreePayout.PayoutFetchTransfer(
                    API_VERSION, null, null, preCheck.getIdempotencyKey().toString(), null);
            gatewayStatus = response.getData().getStatus();
        } catch (com.cashfree.ApiException e) {
            if (e.getCode() == 404) {
                log.error("Cashfree returned 404 for idempotencyKey: {}. Orphaned network request. Refunding.", preCheck.getIdempotencyKey());
                self().refundFailedPayout(transactionId);
                return;
            }
            throw new RuntimeException("Fetch failed, requeueing MQ message.", e);
        }

        switch (gatewayStatus != null ? gatewayStatus.toUpperCase() : "UNKNOWN") {
            case "SUCCESS", "PROCESSED" -> self().settleSuccessfulPayout(transactionId);
            case "FAILED", "REVERSED", "REJECTED" -> self().refundFailedPayout(transactionId);
            case "PENDING", "PROCESSING" -> log.info("Payout txId: {} processing at bank.", transactionId);
            default -> log.warn("Unknown payout status received: {}", gatewayStatus);
        }
    }

    // 4. ISOLATED TRANSACTION BOUNDARIES (LEDGER ESCROW MATH)

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction quarantineInstantPayoutFunds(UUID merchantUserId, BigDecimal amount, UUID idempotencyKey) {
        Wallet merchantWallet = walletRepository.findByUserIdAndLock(merchantUserId).orElseThrow();
        BigDecimal totalDeduction = amount.add(INSTANT_FEE);

        if (merchantWallet.getAvailableBalance().compareTo(totalDeduction) < 0) {
            throw new BadRequestException("Insufficient balance to cover payout and instant fee.");
        }

        // Ledger Math: Deduct total, move principal to processing escrow
        merchantWallet.setAvailableBalance(merchantWallet.getAvailableBalance().subtract(totalDeduction));
        merchantWallet.setProcessingBalance(merchantWallet.getProcessingBalance().add(amount));
        walletRepository.save(merchantWallet);

        Transaction payoutTx = Transaction.builder()
                .senderWallet(merchantWallet).amount(amount)
                .transactionType(TransactionType.PAYOUT).transactionStatus(TransactionStatus.PENDING)
                .idempotencyKey(idempotencyKey).build();

        // FIX: Flush immediately to prevent Hibernate Batch Sorting Warning
        payoutTx = transactionRepository.saveAndFlush(payoutTx);

        Transaction feeTx = Transaction.builder()
                .senderWallet(merchantWallet).amount(INSTANT_FEE)
                .transactionType(TransactionType.FEE).transactionStatus(TransactionStatus.SUCCESS)
                .idempotencyKey(UUID.randomUUID()).originalTransaction(payoutTx).build();
        transactionRepository.save(feeTx);

        return payoutTx;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Transaction> quarantineFullBalanceForStandardPayout(UUID merchantUserId, UUID idempotencyKey) {
        Wallet merchantWallet = walletRepository.findByUserIdAndLock(merchantUserId).orElseThrow();
        BigDecimal amount = merchantWallet.getAvailableBalance();

        if (amount.compareTo(new BigDecimal("10.00")) < 0) {
            return Optional.empty();
        }

        // Ledger Math: Move ALL available balance to processing escrow (No Fee)
        merchantWallet.setAvailableBalance(BigDecimal.ZERO);
        merchantWallet.setProcessingBalance(merchantWallet.getProcessingBalance().add(amount));
        walletRepository.save(merchantWallet);

        Transaction payoutTx = Transaction.builder()
                .senderWallet(merchantWallet).amount(amount)
                .transactionType(TransactionType.PAYOUT).transactionStatus(TransactionStatus.PENDING)
                .idempotencyKey(idempotencyKey).build();

        return Optional.of(transactionRepository.saveAndFlush(payoutTx));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleSuccessfulPayout(UUID transactionId) {
        Transaction tx = transactionRepository.findByIdAndLock(transactionId).orElseThrow();
        if (tx.getTransactionStatus() == TransactionStatus.PENDING) {

            // Principal has left the bank. Remove from escrow.
            Wallet wallet = walletRepository.findByIdAndLock(tx.getSenderWallet().getId()).orElseThrow();
            wallet.setProcessingBalance(wallet.getProcessingBalance().subtract(tx.getAmount()));
            walletRepository.save(wallet);

            tx.setTransactionStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(tx);
            log.info("Ledger reconciled: Payout SUCCESS for txId: {}. Removed from escrow.", transactionId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundFailedPayout(UUID transactionId) {
        Transaction tx = transactionRepository.findByIdAndLock(transactionId).orElseThrow();
        if (tx.getTransactionStatus() == TransactionStatus.PENDING) {

            BigDecimal principalRefund = tx.getAmount();
            BigDecimal feeRefund = BigDecimal.ZERO;

            Optional<Transaction> linkedFeeTxOpt = transactionRepository.findByOriginalTransaction(tx);
            if (linkedFeeTxOpt.isPresent()) {
                Transaction feeTx = linkedFeeTxOpt.get();
                feeRefund = feeTx.getAmount();
                feeTx.setTransactionStatus(TransactionStatus.FAILED);
                transactionRepository.save(feeTx);
            }

            // Move principal out of processing, return principal + fee back to available
            Wallet wallet = walletRepository.findByIdAndLock(tx.getSenderWallet().getId()).orElseThrow();
            wallet.setProcessingBalance(wallet.getProcessingBalance().subtract(principalRefund));
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(principalRefund).add(feeRefund));
            walletRepository.save(wallet);

            tx.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            log.warn("Payout FAILED for txId: {}. Returned ₹{} principal and ₹{} fee to merchant.",
                    transactionId, principalRefund, feeRefund);

            // Fire Async Notification
            User merchantUser = wallet.getUser();
            merchantProfileRepository.findByUserIdWithUser(merchantUser.getId()).ifPresent(merchant -> {
                try {
                    notificationService.sendPayoutFailure(merchantUser.getEmail(), merchant.getBusinessName(), principalRefund);
                } catch (Exception e) {
                    log.error("Ledger updated, but failed to queue Payout Failure email for merchant: {}", merchantUser.getId(), e);
                }
            });
        }
    }
}