package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.transaction.nfc.NfcInitiateRequest;
import com.pratham.paymentx.dto.transaction.nfc.NfcInitiateResponse;
import com.pratham.paymentx.dto.transaction.nfc.NfcProcessRequest;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.NfcPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/transaction/nfc")
@RequiredArgsConstructor
@Slf4j
public class NfcPurchaseController {

    private final NfcPurchaseService nfcPurchaseService;

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/initiate")
    public ResponseEntity<NfcInitiateResponse> initiatePurchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NfcInitiateRequest request) {

        log.info("NFC Initiate requested by merchant: {}", principal.getUserId());
        NfcInitiateResponse response = nfcPurchaseService.initiatePurchase(principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processPurchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NfcProcessRequest request) {

        log.info("NFC Process requested by merchant: {}", principal.getUserId());
        nfcPurchaseService.processPurchase(principal.getUserId(), request);

        // If no exception is thrown, the transaction is 100% successful.
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Payment processed successfully."
        ));
    }
}