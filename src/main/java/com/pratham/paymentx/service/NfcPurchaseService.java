package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.transaction.nfc_payment.NfcInitiateRequest;
import com.pratham.paymentx.dto.transaction.nfc_payment.NfcInitiateResponse;
import com.pratham.paymentx.dto.transaction.nfc_payment.NfcProcessRequest;

import java.util.UUID;

public interface NfcPurchaseService {
    NfcInitiateResponse initiatePurchase(UUID merchantId, NfcInitiateRequest request);
    void processPurchase(UUID merchantId, NfcProcessRequest request);
}