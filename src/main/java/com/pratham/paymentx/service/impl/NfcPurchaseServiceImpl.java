package com.pratham.paymentx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratham.paymentx.dto.transaction.nfc_payment.NfcInitiateRequest;
import com.pratham.paymentx.dto.transaction.nfc_payment.NfcInitiateResponse;
import com.pratham.paymentx.dto.transaction.nfc_payment.NfcProcessRequest;
import com.pratham.paymentx.dto.transaction.nfc_payment.NfcRedisSession;
import com.pratham.paymentx.entity.NfcCard;
import com.pratham.paymentx.entity.Transaction;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.entity.Wallet;
import com.pratham.paymentx.enums.NfcCardStatus;
import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.enums.WalletStatus;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.*;
import com.pratham.paymentx.service.NfcPurchaseService;
import com.pratham.paymentx.service.NotificationService;
import com.pratham.paymentx.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NfcPurchaseServiceImpl implements NfcPurchaseService {

    private final NfcCardRepository nfcCardRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final StudentProfileRepository studentProfileRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    private static final long NFC_SESSION_TTL_SECONDS = 60;

    @Override
    @Transactional(readOnly = true)
    public NfcInitiateResponse initiatePurchase(UUID merchantId, NfcInitiateRequest request) {
        Wallet merchantWallet = walletRepository.findByUserId(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant wallet not found"));
        if (merchantWallet.getWalletStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Merchant wallet is inactive or blocked.");
        }

        NfcCard card = nfcCardRepository.findByChipId(request.getChipId())
                .orElseThrow(() -> new ResourceNotFoundException("Unregistered NFC Card"));
        if (card.getStatus() != NfcCardStatus.ACTIVE) {
            throw new BadRequestException("This NFC card has been deactivated or reported lost.");
        }

        Wallet studentWallet = walletRepository.findByUserId(card.getStudentProfile().getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student wallet not found"));

        if (studentWallet.getWalletStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Student wallet is currently locked or inactive.");
        }

        if (studentWallet.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance in student wallet.");
        }

        // 1. Backend generates the ultimate source of truth
        String sessionToken = UUID.randomUUID().toString();

        NfcRedisSession redisSession = NfcRedisSession.builder()
                .studentWalletId(studentWallet.getId())
                .merchantWalletId(merchantWallet.getId())
                .merchantUserId(merchantId)
                .amount(request.getAmount())
                .build();

        try {
            // 2. The Singleton Session Enforcement (Double-Tap & Hoarding Prevention)
            String pointerKey = "nfc:pointer:" + studentWallet.getId().toString();
            String oldToken = stringRedisTemplate.opsForValue().get(pointerKey);

            // Instantly destroy any previous dangling session for this student
            if (oldToken != null) {
                stringRedisTemplate.delete("nfc:session:" + oldToken);
                log.warn("Destroyed dangling NFC session for student wallet: {}", studentWallet.getId());
            }

            // 3. Save the new session and update the pointer
            String jsonPayload = objectMapper.writeValueAsString(redisSession);
            stringRedisTemplate.opsForValue().set("nfc:session:" + sessionToken, jsonPayload, NFC_SESSION_TTL_SECONDS, TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(pointerKey, sessionToken, NFC_SESSION_TTL_SECONDS, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Failed to serialize NFC session to Redis", e);
            throw new RuntimeException("System error during session initiation");
        }

        return NfcInitiateResponse.builder()
                .nfcSessionToken(sessionToken) // Client MUST return this UUID in /process
                .expiresIn(NFC_SESSION_TTL_SECONDS)
                .studentName(card.getStudentProfile().getFullName())
                .walletStatus(studentWallet.getWalletStatus().name())
                .amount(request.getAmount())
                .build();
    }

    @Override
    @Transactional
    public void processPurchase(UUID merchantId, NfcProcessRequest request) {

        // 1. The Flawless Idempotency Shield
        Optional<Transaction> existingTxOpt = transactionRepository.findByIdempotencyKey(request.getSessionToken());
        if (existingTxOpt.isPresent()) {
            Transaction existingTx = existingTxOpt.get();

            // Defense 1: Cross-Domain Replay Attack Prevention
            if (existingTx.getTransactionType() != TransactionType.PURCHASE) {
                log.error("Security Alert: Idempotency key {} reused across domains.", request.getSessionToken());
                throw new BadRequestException("Invalid or corrupted idempotency key.");
            }

            // Defense 2: Strict State Verification (Defensive Programming)
            // Even though we only write SUCCESS right now, this protects against future code changes.
            if (existingTx.getTransactionStatus() != TransactionStatus.SUCCESS) {
                log.error("System Anomaly: Found non-SUCCESS purchase record for key: {}", request.getSessionToken());
                throw new BadRequestException("Transaction failed or is in an invalid state. Please initiate a new tap.");
            }

            // If it passes both, it is a genuine dropped-network recovery for a successful payment.
            log.info("Network retry absorbed for session: {}. Returning early success.", request.getSessionToken());
            return;
        }

        // 2. Atomic ZERO-TRUST Execution
        String jsonPayload = stringRedisTemplate.opsForValue().getAndDelete("nfc:session:" + request.getSessionToken());
        if (jsonPayload == null) {
            throw new BadRequestException("Payment session expired or has already been consumed. Please tap again.");
        }

        NfcRedisSession session;
        try {
            session = objectMapper.readValue(jsonPayload, NfcRedisSession.class);
        } catch (Exception e) {
            log.error("Corrupted NFC session data in Redis", e);
            throw new RuntimeException("Invalid session data");
        }

        // 3. Contextual Security Check
        if (!session.getMerchantUserId().equals(merchantId)) {
            log.warn("Security Alert: Merchant {} attempted to process session belonging to merchant {}", merchantId, session.getMerchantUserId());
            throw new BadRequestException("Invalid payment execution context.");
        }

        // 4. VERIFY PIN FIRST (Calls isolated transaction, avoiding self-deadlock)
        walletService.verifyWalletPin(session.getStudentWalletId(), request.getPin());

        // 5. Deadlock-Free Ledger Sorting
        UUID studentWalletId = session.getStudentWalletId();
        UUID merchantWalletId = session.getMerchantWalletId();

        UUID firstLockId = studentWalletId.compareTo(merchantWalletId) < 0 ? studentWalletId : merchantWalletId;
        UUID secondLockId = studentWalletId.compareTo(merchantWalletId) < 0 ? merchantWalletId : studentWalletId;

        // 6. Acquire Locks in strict Lexicographical order
        Wallet firstWallet = walletRepository.findByIdAndLock(firstLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet missing during execution"));
        Wallet secondWallet = walletRepository.findByIdAndLock(secondLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet missing during execution"));

        Wallet studentWallet = studentWalletId.equals(firstLockId) ? firstWallet : secondWallet;
        Wallet merchantWallet = merchantWalletId.equals(firstLockId) ? firstWallet : secondWallet;

        // 7. Security Verifications Under Lock
        if (studentWallet.getWalletStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Student wallet was locked during session execution.");
        }

        // 8. Strict Balance Check Under Lock
        if (studentWallet.getAvailableBalance().compareTo(session.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance.");
        }

        // 9. The Ledger Execution
        studentWallet.setAvailableBalance(studentWallet.getAvailableBalance().subtract(session.getAmount()));
        merchantWallet.setAvailableBalance(merchantWallet.getAvailableBalance().add(session.getAmount()));

        Transaction transaction = Transaction.builder()
                .senderWallet(studentWallet)
                .receiverWallet(merchantWallet)
                .amount(session.getAmount())
                .transactionType(TransactionType.PURCHASE)
                .transactionStatus(TransactionStatus.SUCCESS)
                .idempotencyKey(request.getSessionToken()) // Permanently anchors the UUID
                .build();

        try {
            walletRepository.save(studentWallet);
            walletRepository.save(merchantWallet);
            transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            throw new BadRequestException("A concurrent process already utilized this idempotency key.");
        }

        log.info("SUCCESS: Executed offline purchase txId={} for ₹{}", transaction.getId(), session.getAmount());

        // Fire Async Notifications to both parties
        User studentUser = studentWallet.getUser();
        User merchantUser = merchantWallet.getUser();

        try {
            studentProfileRepository.findByUserId(studentUser.getId()).ifPresent(studentProfile -> {
                merchantProfileRepository.findByUserIdWithUser(merchantUser.getId()).ifPresent(merchantProfile -> {

                    notificationService.sendNfcPurchaseReceiptStudent(
                            studentUser.getEmail(),
                            studentProfile.getFullName(),
                            merchantProfile.getBusinessName(),
                            session.getAmount());

                    notificationService.sendNfcPurchaseReceiptMerchant(
                            merchantUser.getEmail(),
                            merchantProfile.getBusinessName(),
                            studentProfile.getFullName(),
                            session.getAmount());
                });
            });
        } catch (Exception e) {
            log.error("Ledger updated, but failed to queue NFC receipt emails for txId={}", transaction.getId(), e);
        }
    }
}