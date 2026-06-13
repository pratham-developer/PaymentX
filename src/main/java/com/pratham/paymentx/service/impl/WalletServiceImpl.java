package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.entity.Wallet;
import com.pratham.paymentx.enums.WalletStatus;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.repository.WalletRepository;
import com.pratham.paymentx.service.WalletService;
import com.pratham.paymentx.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final HashUtil hashUtil;

    private static final int MAX_PIN_ATTEMPTS = 3;

    @Override
    @Transactional
    public void createWallet(UUID userId, String pin) {
        log.info("Initiating wallet creation for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getProfileCompleted()) {
            throw new BadRequestException("Cannot create wallet: Profile is incomplete");
        }

        if (walletRepository.findByUser(user).isPresent()) {
            throw new BadRequestException("Wallet already exists for this user");
        }

        try {
            Wallet newWallet = Wallet.builder()
                    .user(user)
                    .pinHash(hashUtil.hash(pin))
                    .walletStatus(WalletStatus.ACTIVE)
                    .availableBalance(BigDecimal.ZERO)
                    .processingBalance(BigDecimal.ZERO)
                    .pinAttempts(0)
                    .version(0L)
                    .build();

            walletRepository.saveAndFlush(newWallet);
            log.info("Successfully created wallet for userId: {}", userId);

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent wallet creation blocked for userId: {}", userId);
            throw new BadRequestException("Wallet already exists for this user");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verifyWalletPin(UUID walletId, String rawPin) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        if (wallet.getWalletStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Wallet is locked or inactive."); // Hard system stop, rollback is fine here
        }

        // 2. Cryptographic Check
        if (!hashUtil.matches(rawPin, wallet.getPinHash())) {
            int attempts = wallet.getPinAttempts() + 1;
            wallet.setPinAttempts(attempts);

            if (attempts >= MAX_PIN_ATTEMPTS) {
                wallet.setWalletStatus(WalletStatus.LOCKED);
                log.error("SECURITY LOCKOUT: Wallet {} breached max PIN attempts. Account frozen.", walletId);
            }

            try {
                walletRepository.saveAndFlush(wallet);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.warn("Concurrent PIN failure update blocked for wallet {}", walletId);
            }

            // CLEAN SOLUTION: Return false instead of throwing an exception
            return false;
        }

        // 3. Success: Reset the counter if necessary
        if (wallet.getPinAttempts() > 0) {
            wallet.setPinAttempts(0);
            try {
                walletRepository.saveAndFlush(wallet);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.warn("Concurrent PIN reset blocked for wallet {}", walletId);
            }
        }

        return true;
    }
}