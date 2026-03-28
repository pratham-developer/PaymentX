package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.admin.CreateAdminRequest;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {
    private final UserRepository userRepository;

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
}
