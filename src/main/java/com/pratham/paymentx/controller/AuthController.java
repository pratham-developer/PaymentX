package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.auth.*;
import com.pratham.paymentx.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    //public route
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("Attempting to process login request");
        return ResponseEntity.ok(authService.login(request));
    }

    //only students can access
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/student/finish")
    public ResponseEntity<Void> finishStudent(@Valid @RequestBody FinishStudentRequest request){
        log.info("Attempting to finish profile for a student");
        authService.finishStudent(request);
        return ResponseEntity.noContent().build();
    }

    //only merchants can access
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/merchant/finish")
    public ResponseEntity<Void> finishMerchant(@Valid @RequestBody FinishMerchantRequest request){
        log.info("Attempting to finish profile for a merchant");
        authService.finishMerchant(request);
        return ResponseEntity.noContent().build();
    }

    //public route
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestHeader("x-refresh-token") String refreshToken){
        log.info("Attempting to refresh session for a user");
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    //public route
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("x-refresh-token") String refreshToken){
        log.info("Attempting to logout a user");
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }
    //TODO: dashboard route -> gets user details with the recent transactions
}
