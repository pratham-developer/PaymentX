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

    /*
     * Self-proxy via ApplicationContextAware — no field injection, no @Lazy hack.
     *
     * Spring sets this after the bean is fully constructed via setApplicationContext().
     * We resolve the proxy lazily on first use via self() to avoid any circular
     * dependency at startup. The context lookup returns the Spring-managed proxy,
     * so @Transactional on the methods below is correctly honored.
     */
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
    public void processApproval(UUID merchantId) {
        MerchantProfile merchant = merchantProfileRepository
                .findByUserIdWithUser(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MerchantProfile not found for merchantId=" + merchantId));

        MerchantGatewayStatus status = merchant.getGatewayStatus();
        log.info("Processing approval for merchantId={} | currentStatus={}", merchantId, status);

        switch (status) {
            case BENEFICIARY_CREATED, COMPLETED -> {
                log.info("Merchant {} already processed (status={}), skipping", merchantId, status);
                return;
            }
            case FAILED, QUARANTINED -> {
                log.warn("Merchant {} in terminal state {}, skipping", merchantId, status);
                return;
            }
            case PENDING -> self().transitionToProcessing(merchant);
            case PROCESSING -> { /* fall through to Cashfree call */ }
        }

        createBeneficiaryAndActivate(merchant);
    }

    @Override
    @Transactional
    public void transitionToProcessing(MerchantProfile merchant) {
        String beneficiaryId = "MERCHANT_" + merchant.getUser().getId().toString().replace("-", "");
        merchant.setCashfreeBeneficiaryId(beneficiaryId);
        merchant.setGatewayStatus(MerchantGatewayStatus.PROCESSING);
        merchantProfileRepository.save(merchant);
        log.info("Merchant {} transitioned PENDING → PROCESSING, beneficiaryId={}",
                merchant.getUser().getId(), beneficiaryId);
    }

    @Override
    @Transactional
    public void activateMerchant(MerchantProfile merchant) {
        merchant.setGatewayStatus(MerchantGatewayStatus.BENEFICIARY_CREATED);
        merchantProfileRepository.save(merchant);

        User user = merchant.getUser();
        user.setProfileActive(true);
        userRepository.save(user);

        log.info("Merchant {} approved and activated", user.getId());
    }

    @Override
    @Transactional
    public void failMerchant(MerchantProfile merchant) {
        merchant.setEncryptedBankAccount(null);
        merchant.setEncryptedIfsc(null);
        merchant.setEncryptedGstTaxId(null);
        merchant.setBeneficiaryName(null);
        merchant.setCashfreeBeneficiaryId(null);
        merchant.setGatewayStatus(MerchantGatewayStatus.FAILED);
        merchantProfileRepository.save(merchant);

        User user = merchant.getUser();
        user.setProfileCompleted(false);
        userRepository.save(user);

        emailService.sendBankVerificationFailureEmail(user.getEmail(), merchant.getBusinessName());
        log.warn("Merchant {} bank verification failed, profile reset", user.getId());
    }

    // ─── Cashfree interaction ─────────────────────────────────────────────────

    private void createBeneficiaryAndActivate(MerchantProfile merchant) {
        String bankAccount = encryptionUtil.decrypt(merchant.getEncryptedBankAccount());
        String ifsc = encryptionUtil.decrypt(merchant.getEncryptedIfsc());

        CreateBeneficiaryRequest request = buildBeneficiaryRequest(merchant, bankAccount, ifsc);

        try {
            ApiResponse<Beneficiary> response = cashfreePayout
                    .PayoutCreateBeneficiary(API_VERSION, null, request, null);
            handleBeneficiaryStatus(merchant, response.getData().getBeneficiaryStatus());
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.info("Beneficiary already exists for merchantId={}, fetching status",
                        merchant.getUser().getId());
                fetchAndHandleBeneficiaryStatus(merchant);
            } else {
                log.error("Cashfree API error for merchantId={} | code={} | message={}",
                        merchant.getUser().getId(), e.getCode(), e.getMessage());
                throw new RuntimeException("Cashfree beneficiary creation failed: " + e.getMessage(), e);
            }
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
            log.error("Failed to fetch beneficiary for merchantId={} | code={} | message={}",
                    merchant.getUser().getId(), e.getCode(), e.getMessage());
            throw new RuntimeException("Cashfree fetch beneficiary failed", e);
        }
    }

    private void handleBeneficiaryStatus(MerchantProfile merchant,
                                         Beneficiary.BeneficiaryStatusEnum statusEnum) {
        String status = statusEnum != null ? statusEnum.getValue().toUpperCase() : "UNKNOWN";
        UUID merchantId = merchant.getUser().getId();
        log.info("Beneficiary status for merchantId={}: {}", merchantId, status);

        switch (status) {
            case "VERIFIED"                       -> self().activateMerchant(merchant);
            case "INVALID", "FAILED", "CANCELLED" -> self().failMerchant(merchant);
            default -> {
                log.warn("Merchant {} beneficiary status is {}, will retry", merchantId, status);
                throw new RuntimeException("Beneficiary not yet verified, status=" + status);
            }
        }
    }
}