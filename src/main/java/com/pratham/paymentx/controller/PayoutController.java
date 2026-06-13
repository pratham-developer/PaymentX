package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.transaction.payout.PayoutInitiateRequest;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/transaction/payout")
@RequiredArgsConstructor
@Slf4j
public class PayoutController {

    private final PayoutService payoutService;

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/instant")
    public ResponseEntity<Map<String, String>> initiateInstantPayout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PayoutInitiateRequest request) {

        log.info("Manual Instant Payout requested by merchant: {}", principal.getUserId());

        payoutService.initiateInstantPayout(principal.getUserId(), request.getAmount());

        return ResponseEntity.accepted().body(Map.of(
                "status", "PROCESSING",
                "message", "Instant Payout initiated. Funds will arrive shortly."
        ));
    }
}