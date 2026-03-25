package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.auth.GoogleAccount;
import com.pratham.paymentx.dto.auth.GoogleLoginRequest;
import com.pratham.paymentx.dto.auth.TokenResponse;
import com.pratham.paymentx.security.GoogleIdentityProvider;
import com.pratham.paymentx.service.AuthPersistenceService;
import com.pratham.paymentx.service.AuthService;
import com.pratham.paymentx.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final GoogleIdentityProvider googleIdentityProvider;
    private final SessionService sessionService;
    private final AuthPersistenceService authPersistenceService;

    @Override
    public TokenResponse login(GoogleLoginRequest request) {
        // TODO: [REDIS RATE LIMITER]
        // This acts as the shield to protect the db connection pool
        log.info("Processing login request for device fingerprint: {}", request.getDeviceFingerprint());
        GoogleAccount googleAccount = googleIdentityProvider.verifyGoogleId(request.getIdToken());
        UUID userId = authPersistenceService.resolveAndPersistUser(googleAccount);
        TokenResponse tokenResponse = sessionService.createSession(userId, request.getDeviceFingerprint());
        log.info("Successfully processed login request for device fingerprint: {}", request.getDeviceFingerprint());
        return tokenResponse;
    }

    // TODO: Admin route to create new admin
    // in that case, only a user row with email and role will be created
    // now when the user logs in for the first time, his google account will be auto attached
}