package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.wallet.WalletCreateRequest;
import com.pratham.paymentx.exception.BadRequestException;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createWallet(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WalletCreateRequest request) {

        if (!request.getPin().equals(request.getConfirmPin())) {
            throw new BadRequestException("PIN and Confirm PIN do not match");
        }

        walletService.createWallet(principal.getUserId(), request.getPin());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Wallet created successfully"));
    }
}