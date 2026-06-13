package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.dashboard.DashboardResponse;
import com.pratham.paymentx.dto.transaction.TransactionDto;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboardData(
            @AuthenticationPrincipal UserPrincipal principal) {

        DashboardResponse response = dashboardService.getDashboard(principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<PagedModel<TransactionDto>> getTransactionFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);
        Page<TransactionDto> feed = dashboardService.getTransactionFeed(principal.getUserId(), page, safeSize);
        return ResponseEntity.ok(new PagedModel<>(feed));
    }
}