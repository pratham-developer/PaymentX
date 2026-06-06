package com.pratham.paymentx.service;

import java.util.UUID;

public interface WalletService {
    void createWallet(UUID userId, String pin);
    void verifyWalletPin(UUID walletId, String rawPin);
}