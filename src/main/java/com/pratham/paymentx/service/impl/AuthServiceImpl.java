package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.auth.FinishStudentRequest;
import com.pratham.paymentx.dto.auth.GoogleAccount;
import com.pratham.paymentx.dto.auth.GoogleLoginRequest;
import com.pratham.paymentx.dto.auth.TokenResponse;
import com.pratham.paymentx.entity.NfcCard;
import com.pratham.paymentx.entity.StudentProfile;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.NfcCardStatus;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.exception.InvalidRoleException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.NfcCardRepository;
import com.pratham.paymentx.repository.StudentProfileRepository;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.security.GoogleIdentityProvider;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.AuthPersistenceService;
import com.pratham.paymentx.service.AuthService;
import com.pratham.paymentx.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final GoogleIdentityProvider googleIdentityProvider;
    private final SessionService sessionService;
    private final AuthPersistenceService authPersistenceService;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NfcCardRepository nfcCardRepository;

    @Override
    public UserPrincipal getCurrentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Override
    public TokenResponse login(GoogleLoginRequest request) {
        // TODO: [REDIS RATE LIMITER]
        // This acts as the shield to protect the db connection pool
        log.info("Processing a login request");
        GoogleAccount googleAccount = googleIdentityProvider.verifyGoogleId(request.getIdToken());
        //independent transaction runs for user
        UUID userId = authPersistenceService.resolveAndPersistUser(googleAccount);
        //another independent transaction runs for session creation
        TokenResponse tokenResponse = sessionService.createSession(userId);
        log.info("Successfully processed the login request for userId: {}",userId);
        return tokenResponse;
    }

    @Override
    @Transactional
    public void finishStudent(FinishStudentRequest request) {
        UserPrincipal userPrincipal = getCurrentPrincipal();
        log.info("Finishing profile for a student with email: {}",userPrincipal.getEmail());
        User user = userRepository.findById(userPrincipal.getUserId()).orElseThrow(
                () -> new ResourceNotFoundException("User not found")
        );

        if (!Role.STUDENT.equals(user.getRole())) {
            throw new InvalidRoleException("User is not a STUDENT");
        }
        if (user.getProfileCompleted()) {
            throw new BadRequestException("Profile is already completed");
        }

        //check if card is not mapped with another account
        if (nfcCardRepository.existsByChipId(request.getChipId())) {
            throw new BadRequestException("IdCard is already mapped to another account");
        }

        //load student profile
        StudentProfile studentProfile = studentProfileRepository.findByUser(user).orElseThrow(
                () -> new ResourceNotFoundException("Profile does not exist for the user")
        );

        //dirty check auto saves studentProfile and user
        studentProfile.setPhone(request.getPhone());
        user.setProfileCompleted(true);

        //build nfc card
        NfcCard nfcCard = NfcCard.builder()
                .studentProfile(studentProfile)
                .chipId(request.getChipId())
                .status(NfcCardStatus.ACTIVE)
                .build();
        nfcCardRepository.save(nfcCard);
        log.info("Successfully finished profile for a student with email: {}",userPrincipal.getEmail());
    }

    // TODO: Admin route to create new admin
    // in that case, only a user row with email and role will be created
    // now when the user logs in for the first time, his google account will be auto attached

    // TODO: Firebase Attestation for android clients to prevent only app to send requests
    // TODO: enable google play integrity api for anti-root check

    // TODO: dashboard route with user details, profile completed, profile active, wallet created, blocked or unblocked, recent transactions
}