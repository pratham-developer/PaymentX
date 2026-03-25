package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.auth.TokenResponse;

import java.util.UUID;

public interface SessionService {
    TokenResponse createSession(UUID userId, String deviceFingerprint);
}
