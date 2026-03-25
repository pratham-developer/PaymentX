package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.auth.GoogleLoginRequest;
import com.pratham.paymentx.dto.auth.TokenResponse;

public interface AuthService {
    TokenResponse login(GoogleLoginRequest request);
}
