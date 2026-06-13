package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import com.pratham.paymentx.projection.TransactionStats;
import com.pratham.paymentx.repository.TransactionRepository;
import com.pratham.paymentx.repository.WalletRepository;
import com.pratham.paymentx.service.FinancialReportingService;
import com.pratham.paymentx.service.NotificationService;
import com.pratham.paymentx.projection.MerchantFlowStats;
import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import com.pratham.paymentx.repository.MerchantProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialReportingServiceImpl implements FinancialReportingService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final NotificationService notificationService;
    private final MerchantProfileRepository merchantProfileRepository;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Override
    @Transactional(readOnly = true)
    public void generateAndSendAdminEodReport(LocalDate targetDate) {
        log.info("Generating Admin EOD Report for date: {}", targetDate);

        // 1. Calculate Exact 24-Hour Time Boundaries for the target date
        ZonedDateTime startOfDay = targetDate.atStartOfDay(IST_ZONE);
        OffsetDateTime startTime = startOfDay.toOffsetDateTime();
        OffsetDateTime endTime = startOfDay.plusDays(1).toOffsetDateTime();

        // 2. Execute High-Speed DB Aggregations
        TransactionStats topupStats = transactionRepository.getTransactionStats(
                TransactionType.TOPUP, TransactionStatus.SUCCESS, startTime, endTime);

        TransactionStats payoutStats = transactionRepository.getTransactionStats(
                TransactionType.PAYOUT, TransactionStatus.SUCCESS, startTime, endTime);

        TransactionStats feeStats = transactionRepository.getTransactionStats(
                TransactionType.FEE, TransactionStatus.SUCCESS, startTime, endTime);

        BigDecimal totalEscrowLiability = walletRepository.getTotalProcessingEscrowBalance();

        // 3. Dispatch to MQ via Notification Facade
        notificationService.sendAdminEodReport(
                targetDate,
                topupStats.getTxCount(),
                topupStats.getTotalVolume(),
                payoutStats.getTxCount(),
                payoutStats.getTotalVolume(),
                feeStats.getTotalVolume(), // Total Platform Revenue
                totalEscrowLiability
        );

        log.info("Successfully enqueued Admin EOD Report for {}", targetDate);
    }

    @Override
    @Transactional(readOnly = true)
    public void generateAndSendMerchantDailyStatements(LocalDate targetDate) {
        log.info("Generating Merchant Daily Statements for date: {}", targetDate);

        ZonedDateTime startOfDay = targetDate.atStartOfDay(IST_ZONE);
        OffsetDateTime startTime = startOfDay.toOffsetDateTime();
        OffsetDateTime endTime = startOfDay.plusDays(1).toOffsetDateTime();

        int page = 0;
        int size = 500; // Chunk size: Safe for memory and fast to process
        Page<MerchantProfile> merchantPage;

        do {
            merchantPage = merchantProfileRepository.findByGatewayStatus(
                    MerchantGatewayStatus.BENEFICIARY_CREATED,
                    PageRequest.of(page, size)
            );

            for (MerchantProfile merchant : merchantPage.getContent()) {
                processSingleMerchantStatement(merchant, targetDate, startTime, endTime);
            }

            page++;
        } while (merchantPage.hasNext());

        log.info("Completed dispatching Merchant Daily Statements for {}", targetDate);
    }

    private void processSingleMerchantStatement(MerchantProfile merchant, LocalDate targetDate, OffsetDateTime startTime, OffsetDateTime endTime) {
        walletRepository.findByUserId(merchant.getUser().getId()).ifPresent(wallet -> {

            // 1. Calculate Opening Balance (Time-Travel Ledger Truth at 00:00)
            BigDecimal openingBalance = transactionRepository.getHistoricalBalanceAt(wallet.getId(), startTime);

            // 2. Fetch 24-hour flows
            MerchantFlowStats flows = transactionRepository.getMerchantFlowsForPeriod(wallet.getId(), startTime, endTime);

            // Optimization: Do not spam inactive accounts with $0.00 statements
            if (openingBalance.compareTo(BigDecimal.ZERO) == 0 &&
                    flows.getInflows().compareTo(BigDecimal.ZERO) == 0 &&
                    flows.getOutflows().compareTo(BigDecimal.ZERO) == 0) {
                return;
            }

            // 3. Dispatch to MQ
            notificationService.sendDailyMerchantStatement(
                    merchant.getUser().getEmail(),
                    merchant.getBusinessName(),
                    targetDate,
                    openingBalance,
                    flows.getInflows(),
                    flows.getOutflows(),
                    flows.getFees()
            );
        });
    }
}