package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.transaction.TopupInitiateRequest;
import com.pratham.paymentx.dto.transaction.TopupInitiateResponse;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.TopupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transaction/topup")
@RequiredArgsConstructor
@Slf4j
public class TopupController {

    private final TopupService topupService;

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/initiate")
    public ResponseEntity<TopupInitiateResponse> initiateTopup(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TopupInitiateRequest request) {

        return ResponseEntity.ok(topupService.initiateTopup(principal.getUserId(), request));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<Map<String, String>> verifyTopupClient(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal UserPrincipal principal) {

        topupService.verifyTransactionOwnership(transactionId, principal.getUserId());
        topupService.executeFulfillmentEngine(transactionId);

        return ResponseEntity.ok(Map.of("message", "Payment verification completed"));
    }
}