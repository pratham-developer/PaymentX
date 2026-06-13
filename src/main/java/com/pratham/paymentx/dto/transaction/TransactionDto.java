package com.pratham.paymentx.dto.transaction;

import com.pratham.paymentx.enums.TransactionStatus;
import com.pratham.paymentx.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionDto {
    private UUID id;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private String direction; // "CREDIT" or "DEBIT"
    private String title;     // "Paid to Enzo", "Wallet Top-up"
    private OffsetDateTime timestamp;
}