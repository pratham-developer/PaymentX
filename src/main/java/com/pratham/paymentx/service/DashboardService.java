package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.dashboard.DashboardResponse;
import com.pratham.paymentx.dto.transaction.TransactionDto;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface DashboardService {
    DashboardResponse getDashboard(UUID userId);
    Page<TransactionDto> getTransactionFeed(UUID userId, int page, int size);
}