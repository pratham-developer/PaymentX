package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.dashboard.DashboardResponse;
import com.pratham.paymentx.entity.*;
import com.pratham.paymentx.enums.NfcCardStatus;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.enums.WalletStatus;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.*;
import com.pratham.paymentx.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final NfcCardRepository nfcCardRepository;

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

        // ADMIN Route: Terminate flow here (Admins have no wallets/cards)
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

        // Role-Specific Sub-routines
        return switch (user.getRole()) {
            case STUDENT -> getStudentDashboard(user, wallet);
            case MERCHANT -> getMerchantDashboard(user, wallet);
            default -> throw new IllegalStateException("Unexpected role encountered");
        };
    }

    // Routing Sub-Routines

    private DashboardResponse getStudentDashboard(User user, Wallet wallet) {
        StudentProfile profile = studentProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Student Profile missing"));

        Optional<NfcCard> cardOpt = nfcCardRepository.findByStudentProfile(profile);
        boolean cardActive = cardOpt.isPresent() && cardOpt.get().getStatus() == NfcCardStatus.ACTIVE;

        // Steps 6 & 7: Even if card is blocked, we return full account details
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
                .build();
    }

    private DashboardResponse getMerchantDashboard(User user, Wallet wallet) {
        MerchantProfile profile = merchantProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant Profile missing"));

        // Step 7 for Merchants
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
}