package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.auth.*;
import com.pratham.paymentx.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    //login or register
    //public route
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("Attempting to process login request for device fingerprint: {}", request.getDeviceFingerprint());
        return ResponseEntity.ok(authService.login(request));
    }

//    //only accessible by users with role = student
//    //service method gets the user from security context
//    @PostMapping("/finish/student")
//    public ResponseEntity<TokenResponse> finishStudent(@Valid @RequestBody FinishStudentRequest request){
//        log.info("Attempting to finish profile for a student");
//        return ResponseEntity.ok(authService.finishStudent(request));
//    }
//
//    //only accessible by users with role = merchant
//    //service method gets the user from security context
//    @PostMapping("/finish/merchant")
//    public ResponseEntity<TokenResponse> finishStudent(@Valid @RequestBody FinishMerchantRequest request){
//        log.info("Attempting to finish profile for a merchant");
//        return ResponseEntity.ok(authService.finishMerchant(request));
//    }
//
//    //public route
//    @PostMapping("/refresh")
//    public ResponseEntity<TokenResponse> refresh(@RequestHeader("x-refresh-token") String refreshToken){
//        log.info("Attempting to refresh user");
//        return ResponseEntity.ok(authService.refresh(refreshToken));
//    }
//
//    //requires user to be authenticated
//    //user will be fetched from security context
//    //refresh token is required to handle the session corresponding to it
//    @PostMapping("/logout")
//    public ResponseEntity<Void> logout(@RequestHeader("x-refresh-token") String refreshToken){
//        log.info("Attempting to logout user");
//        authService.logout(refreshToken);
//        return ResponseEntity.noContent().build();
//    }
}
