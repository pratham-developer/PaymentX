package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.auth.TokenResponse;

import java.util.Optional;
import java.util.UUID;

public interface SessionService {
    TokenResponse createSession(UUID userId);
    Optional<TokenResponse> refreshSession(String refreshToken);
}
