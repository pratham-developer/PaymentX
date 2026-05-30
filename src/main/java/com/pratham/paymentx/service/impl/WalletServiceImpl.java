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

    @Override
    @Transactional
    public void createWallet(UUID userId, String pin) {
        log.info("Initiating wallet creation for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Business Rule: Ensure user has actually completed their profile first
        if (!user.getProfileCompleted()) {
            throw new BadRequestException("Cannot create wallet: Profile is incomplete");
        }

        // Prevent double creation
        if (walletRepository.findByUser(user).isPresent()) {
            throw new BadRequestException("Wallet already exists for this user");
        }

        try {
            Wallet newWallet = Wallet.builder()
                    .user(user)
                    .pinHash(hashUtil.hash(pin))
                    .walletStatus(WalletStatus.ACTIVE)
                    // The @Builder.Default in your Wallet entity already handles balances,
                    // pinAttempts, and version, but setting status explicitly is good practice.
                    .availableBalance(BigDecimal.ZERO)
                    .processingBalance(BigDecimal.ZERO)
                    .pinAttempts(0)
                    .version(0L)
                    .build();

            walletRepository.saveAndFlush(newWallet);
            log.info("Successfully created wallet for userId: {}", userId);

        } catch (DataIntegrityViolationException e) {
            // Failsafe: Catches race conditions if two requests bypass the .isPresent() check
            log.warn("Concurrent wallet creation blocked for userId: {}", userId);
            throw new BadRequestException("Wallet already exists for this user");
        }
    }
}