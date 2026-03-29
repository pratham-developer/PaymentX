package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.auth.FinishMerchantRequest;
import com.pratham.paymentx.dto.auth.FinishStudentRequest;
import com.pratham.paymentx.dto.auth.GoogleLoginRequest;
import com.pratham.paymentx.dto.auth.TokenResponse;
import com.pratham.paymentx.security.UserPrincipal;

public interface AuthService {
    UserPrincipal getCurrentPrincipal();
    TokenResponse login(GoogleLoginRequest request);
    void finishStudent(FinishStudentRequest request);
    void finishMerchant(FinishMerchantRequest request);
    TokenResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
