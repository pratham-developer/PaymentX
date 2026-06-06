package com.pratham.paymentx.service.impl;

import com.cashfree.ApiException;
import com.cashfree.ApiResponse;
import com.cashfree.CashfreePayout;
import com.cashfree.model.Beneficiary;
import com.cashfree.model.CreateBeneficiaryRequest;
import com.cashfree.model.CreateBeneficiaryRequestBeneficiaryContactDetails;
import com.cashfree.model.CreateBeneficiaryRequestBeneficiaryInstrumentDetails;
import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.MerchantProfileRepository;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.service.EmailService;
import com.pratham.paymentx.service.MerchantApprovalService;
import com.pratham.paymentx.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantApprovalServiceImpl implements MerchantApprovalService, ApplicationContextAware {

    private final MerchantProfileRepository merchantProfileRepository;
    private final UserRepository userRepository;
    private final CashfreePayout cashfreePayout;
    private final EncryptionUtil encryptionUtil;
    private final EmailService emailService;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private MerchantApprovalService self() {
        return applicationContext.getBean(MerchantApprovalService.class);
    }

    private static final String API_VERSION = "2024-01-01";

    @Override
    // 1. NO @Transactional HERE. Orchestrator holds no DB connections during HTTP calls.
    public void processApproval(UUID merchantId) {
        MerchantProfile merchant = merchantProfileRepository
                .findByUserIdWithUser(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        MerchantGatewayStatus status = merchant.getGatewayStatus();
        log.info("Processing approval for merchantId={} | currentStatus={}", merchantId, status);

        switch (status) {
            case BENEFICIARY_CREATED, COMPLETED, FAILED, QUARANTINED -> {
                log.info("Merchant {} in terminal state ({}), skipping", merchantId, status);
                return;
            }
            case PENDING -> {
                // 2. ISOLATED TRANSACTION: Generate ID and flush to DB immediately
                String generatedBeneficiaryId = self().transitionToProcessing(merchantId);

                // Update our detached memory object so the HTTP payload builder has it
                merchant.setCashfreeBeneficiaryId(generatedBeneficiaryId);

                // 3. NETWORK I/O: Execute downstream
                createBeneficiaryAndActivate(merchant);
            }
            case PROCESSING -> {
                // 4. RETRY HANDLING: If status is already processing, the app crashed previously.
                // We MUST fetch downstream to avoid blindly sending a duplicate creation payload.
                log.info("Retry detected. Verifying downstream state before creation.");
                fetchAndHandleBeneficiaryStatus(merchant);
            }
        }
    }

    // Isolated Transactional Boundaries

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String transitionToProcessing(UUID merchantId) {
        // Loads a pristine, attached entity in a fresh persistence context
        MerchantProfile merchant = merchantProfileRepository.findByUserIdWithUser(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        String beneficiaryId = "MERCHANT_" + merchantId.toString().replace("-", "");
        merchant.setCashfreeBeneficiaryId(beneficiaryId);
        merchant.setGatewayStatus(MerchantGatewayStatus.PROCESSING);

        merchantProfileRepository.saveAndFlush(merchant);
        log.info("Merchant {} transitioned PENDING → PROCESSING, beneficiaryId={}", merchantId, beneficiaryId);

        return beneficiaryId;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateMerchant(UUID merchantId) {
        MerchantProfile merchant = merchantProfileRepository.findByUserIdWithUser(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        merchant.setGatewayStatus(MerchantGatewayStatus.BENEFICIARY_CREATED);
        User user = merchant.getUser();
        user.setProfileActive(true);

        merchantProfileRepository.save(merchant);
        userRepository.save(user);

        log.info("Merchant {} approved and activated", merchantId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failMerchant(UUID merchantId) {
        MerchantProfile merchant = merchantProfileRepository.findByUserIdWithUser(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        merchant.setEncryptedBankAccount(null);
        merchant.setEncryptedIfsc(null);
        merchant.setEncryptedGstTaxId(null);
        merchant.setBeneficiaryName(null);
        merchant.setCashfreeBeneficiaryId(null);
        merchant.setGatewayStatus(MerchantGatewayStatus.FAILED);

        User user = merchant.getUser();
        user.setProfileCompleted(false);

        merchantProfileRepository.save(merchant);
        userRepository.save(user);

        emailService.sendBankVerificationFailureEmail(user.getEmail(), merchant.getBusinessName());
        log.warn("Merchant {} bank verification failed, profile reset", merchantId);
    }

    // Cashfree Interaction (Runs OUTSIDE Database Transactions)

    private void createBeneficiaryAndActivate(MerchantProfile merchant) {
        String bankAccount = encryptionUtil.decrypt(merchant.getEncryptedBankAccount());
        String ifsc = encryptionUtil.decrypt(merchant.getEncryptedIfsc());

        CreateBeneficiaryRequest request = buildBeneficiaryRequest(merchant, bankAccount, ifsc);

        try {
            ApiResponse<Beneficiary> response = cashfreePayout
                    .PayoutCreateBeneficiary(API_VERSION, null, request, null);
            handleBeneficiaryStatus(merchant, response.getData().getBeneficiaryStatus());

        } catch (ApiException e) {
            log.error("Code={} Response={} Message={}", e.getCode(), e.getResponseBody(), e.getMessage());

            if (e.getCode() == 409) {
                // 5. TERMINAL DOWNSTREAM CONFLICT: Bank Account registered to a completely different user.
                log.error("CRITICAL: Bank Account registered elsewhere for merchantId={}. Failing profile.",
                        merchant.getUser().getId());
                self().failMerchant(merchant.getUser().getId());
                return; // Return cleanly to acknowledge message
            }

            // Transient errors (500, network loss) throw and trigger the RabbitMQ backoff
            throw new RuntimeException("Cashfree beneficiary creation failed: " + e.getMessage(), e);
        }
    }

    @NotNull
    private static CreateBeneficiaryRequest buildBeneficiaryRequest(
            MerchantProfile merchant, String bankAccount, String ifsc) {

        CreateBeneficiaryRequestBeneficiaryContactDetails contactDetails =
                new CreateBeneficiaryRequestBeneficiaryContactDetails();
        contactDetails.setBeneficiaryEmail(merchant.getUser().getEmail());
        contactDetails.setBeneficiaryPhone(merchant.getPhone());

        CreateBeneficiaryRequestBeneficiaryInstrumentDetails instrumentDetails =
                new CreateBeneficiaryRequestBeneficiaryInstrumentDetails();
        instrumentDetails.setBankAccountNumber(bankAccount);
        instrumentDetails.setBankIfsc(ifsc);

        CreateBeneficiaryRequest request = new CreateBeneficiaryRequest();
        request.setBeneficiaryId(merchant.getCashfreeBeneficiaryId());
        request.setBeneficiaryName(merchant.getBeneficiaryName());
        request.setBeneficiaryContactDetails(contactDetails);
        request.setBeneficiaryInstrumentDetails(instrumentDetails);
        return request;
    }

    private void fetchAndHandleBeneficiaryStatus(MerchantProfile merchant) {
        try {
            ApiResponse<Beneficiary> response = cashfreePayout
                    .PayoutFetchBeneficiary(API_VERSION, null,
                            merchant.getCashfreeBeneficiaryId(), null, null, null);
            handleBeneficiaryStatus(merchant, response.getData().getBeneficiaryStatus());

        } catch (ApiException e) {
            if (e.getCode() == 404) {
                // 6. SELF HEALING: The previous CREATE attempt never reached Cashfree due to a timeout.
                // Cashfree returned a 404 on retry, meaning it is safe to attempt creation now.
                log.warn("Deterministic ID {} not found on downstream retry. Attempting creation.",
                        merchant.getCashfreeBeneficiaryId());
                createBeneficiaryAndActivate(merchant);
                return;
            }
            throw new RuntimeException("Cashfree fetch beneficiary failed", e);
        }
    }

    private void handleBeneficiaryStatus(MerchantProfile merchant, Beneficiary.BeneficiaryStatusEnum statusEnum) {
        String status = statusEnum != null ? statusEnum.getValue().toUpperCase() : "UNKNOWN";
        UUID merchantId = merchant.getUser().getId();
        log.info("Beneficiary status for merchantId={}: {}", merchantId, status);

        switch (status) {
            case "VERIFIED"                       -> self().activateMerchant(merchantId);
            case "INVALID", "FAILED", "CANCELLED" -> self().failMerchant(merchantId);
            default -> {
                log.warn("Merchant {} beneficiary status is {}, will retry", merchantId, status);
                throw new RuntimeException("Beneficiary not yet verified, status=" + status);
            }
        }
    }
}