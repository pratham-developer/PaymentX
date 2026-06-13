package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.dashboard.DashboardResponse;
import com.pratham.paymentx.dto.transaction.TransactionDto;
import com.pratham.paymentx.entity.*;
import com.pratham.paymentx.enums.NfcCardStatus;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.enums.WalletStatus;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.*;
import com.pratham.paymentx.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final NfcCardRepository nfcCardRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID userId) {
        log.info("Evaluating dashboard state machine for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Check Profile Completed
        if (!user.getProfileCompleted()) {
            return buildEarlyExit(user.getRole(), false, user.getProfileActive());
        }

        // 2. Check Profile Active
        if (!user.getProfileActive()) {
            return buildEarlyExit(user.getRole(), true, false);
        }

        // ADMIN Route: Terminate flow here
        if (user.getRole() == Role.ADMIN) {
            return buildAdminDashboard();
        }

        // 3. Check Wallet Created
        Optional<Wallet> walletOpt = walletRepository.findByUser(user);
        if (walletOpt.isEmpty()) {
            DashboardResponse response = buildEarlyExit(user.getRole(), true, true);
            response.setWalletCreated(false);
            return response;
        }

        Wallet wallet = walletOpt.get();

        // 4. Wallet Archived & 5. Wallet Locked
        if (wallet.getWalletStatus() == WalletStatus.ARCHIVED || wallet.getWalletStatus() == WalletStatus.LOCKED) {
            DashboardResponse response = buildEarlyExit(user.getRole(), true, true);
            response.setWalletCreated(true);
            response.setWalletStatus(wallet.getWalletStatus());
            return response;
        }

        // 6. Fetch Recent Transactions (Limit 5)
        List<Transaction> recentTxs = transactionRepository.findRecentTransactionsByWalletId(
                wallet.getId(), PageRequest.of(0, 5)
        );
        List<TransactionDto> mappedTransactions = recentTxs.stream()
                .map(tx -> mapToTransactionDto(tx, wallet.getId()))
                .collect(Collectors.toList());

        // Role-Specific Sub-routines
        return switch (user.getRole()) {
            case STUDENT -> getStudentDashboard(user, wallet, mappedTransactions);
            case MERCHANT -> getMerchantDashboard(user, wallet, mappedTransactions);
            default -> throw new IllegalStateException("Unexpected role encountered");
        };
    }

    // Routing Sub-Routines

    private DashboardResponse getStudentDashboard(User user, Wallet wallet, List<TransactionDto> transactions) {
        StudentProfile profile = studentProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Student Profile missing"));

        Optional<NfcCard> cardOpt = nfcCardRepository.findByStudentProfile(profile);
        boolean cardActive = cardOpt.isPresent() && cardOpt.get().getStatus() == NfcCardStatus.ACTIVE;

        return DashboardResponse.builder()
                .role(Role.STUDENT)
                .profileCompleted(true)
                .profileActive(true)
                .walletCreated(true)
                .walletStatus(wallet.getWalletStatus())
                .cardActive(cardActive)
                .name(profile.getFullName())
                .collegeRegNo(profile.getCollegeRegNo())
                .phone(profile.getPhone())
                .availableBalance(wallet.getAvailableBalance())
                .processingBalance(wallet.getProcessingBalance())
                .totalBalance(calculateTotalBalance(wallet))
                .recentTransactions(transactions)
                .build();
    }

    private DashboardResponse getMerchantDashboard(User user, Wallet wallet, List<TransactionDto> transactions) {
        MerchantProfile profile = merchantProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant Profile missing"));

        return DashboardResponse.builder()
                .role(Role.MERCHANT)
                .profileCompleted(true)
                .profileActive(true)
                .walletCreated(true)
                .walletStatus(wallet.getWalletStatus())
                .name(profile.getBusinessName())
                .phone(profile.getPhone())
                .gatewayStatus(profile.getGatewayStatus())
                .availableBalance(wallet.getAvailableBalance())
                .processingBalance(wallet.getProcessingBalance())
                .totalBalance(calculateTotalBalance(wallet))
                .recentTransactions(transactions)
                .build();
    }

    // Helpers

    private DashboardResponse buildEarlyExit(Role role, boolean completed, boolean active) {
        return DashboardResponse.builder()
                .role(role)
                .profileCompleted(completed)
                .profileActive(active)
                .build();
    }

    private DashboardResponse buildAdminDashboard() {
        return DashboardResponse.builder()
                .role(Role.ADMIN)
                .profileCompleted(true)
                .profileActive(true)
                .build();
    }

    private BigDecimal calculateTotalBalance(Wallet wallet) {
        return wallet.getAvailableBalance().add(wallet.getProcessingBalance());
    }

    // Transaction Mapper - Generates UI-Friendly Dynamic Titles
    private TransactionDto mapToTransactionDto(Transaction tx, UUID myWalletId) {
        boolean isCredit = tx.getReceiverWallet() != null && tx.getReceiverWallet().getId().equals(myWalletId);
        String direction = isCredit ? "CREDIT" : "DEBIT";
        String title = "Transaction";

        switch (tx.getTransactionType()) {
            case TOPUP -> title = "Wallet Top-up";
            case PAYOUT -> title = "Bank Transfer";
            case FEE -> title = "Instant Payout Fee";
            case PURCHASE -> {
                if (isCredit) {
                    // I am the merchant receiving funds. Safely traverse the Sender chain.
                    String studentName = Optional.ofNullable(tx.getSenderWallet())
                            .map(Wallet::getUser)
                            .map(User::getId)
                            .flatMap(studentProfileRepository::findByUserId)
                            .map(StudentProfile::getFullName)
                            .orElse("Student");

                    title = "Received from " + studentName;
                } else {
                    // I am the student paying funds. Safely traverse the Receiver chain.
                    String merchantName = Optional.ofNullable(tx.getReceiverWallet())
                            .map(Wallet::getUser)
                            .map(User::getId)
                            .flatMap(merchantProfileRepository::findByUserId)
                            .map(MerchantProfile::getBusinessName)
                            .orElse("Campus Merchant");

                    title = "Paid to " + merchantName;
                }
            }
        }

        return TransactionDto.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .type(tx.getTransactionType())
                .status(tx.getTransactionStatus())
                .direction(direction)
                .title(title)
                .timestamp(tx.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionDto> getTransactionFeed(UUID userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        // Fetch the raw page of entities from the database
        Page<Transaction> transactionPage = transactionRepository.findAllTransactionsByWalletId(
                wallet.getId(), PageRequest.of(page, size)
        );

        // Elegantly map the entities to DTOs while retaining pagination metadata
        return transactionPage.map(tx -> mapToTransactionDto(tx, wallet.getId()));
    }
}