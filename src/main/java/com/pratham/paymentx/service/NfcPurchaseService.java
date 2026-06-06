package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.transaction.nfc.NfcInitiateRequest;
import com.pratham.paymentx.dto.transaction.nfc.NfcInitiateResponse;
import com.pratham.paymentx.dto.transaction.nfc.NfcProcessRequest;

import java.util.UUID;

public interface NfcPurchaseService {
    NfcInitiateResponse initiatePurchase(UUID merchantId, NfcInitiateRequest request);
    void processPurchase(UUID merchantId, NfcProcessRequest request);
}