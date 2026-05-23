package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.admin.CreateAdminRequest;
import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.messaging.publisher.MerchantApprovalPublisher;
import com.pratham.paymentx.repository.MerchantProfileRepository;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {
    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final MerchantApprovalPublisher merchantApprovalPublisher;

    @Override
    @Transactional
    public void createAdmin(CreateAdminRequest request) {
        log.info("Attempting to create admin with email: {}",request.getEmail());
        if(userRepository.existsByEmail(request.getEmail())){
            throw new BadRequestException("User already exists with this email");
        }
        User user = User.builder()
                .email(request.getEmail())
                .role(Role.ADMIN)
                .profileActive(true)
                .profileCompleted(false)
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void approveMerchant(UUID merchantId) {
        MerchantProfile merchant = merchantProfileRepository
                .findByUserId(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Merchant not found for id=" + merchantId));

        MerchantGatewayStatus status = merchant.getGatewayStatus();

        switch (status) {
            case PENDING, FAILED -> {
                // Reset to PENDING if coming from FAILED so the consumer
                // treats it as a fresh start through the state machine
                if (status == MerchantGatewayStatus.FAILED) {
                    merchant.setGatewayStatus(MerchantGatewayStatus.PENDING);
                    merchantProfileRepository.save(merchant);
                }
                merchantApprovalPublisher.publish(merchantId);
                log.info("Admin triggered approval for merchantId={}", merchantId);
            }
            case PROCESSING -> throw new BadRequestException(
                    "Merchant approval is already in progress");
            case BENEFICIARY_CREATED, COMPLETED -> throw new BadRequestException(
                    "Merchant is already approved and active");
            case QUARANTINED -> throw new BadRequestException(
                    "Merchant is quarantined — they must re-submit bank details before re-approval");
        }
    }
}
