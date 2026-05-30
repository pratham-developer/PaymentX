package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.dashboard.DashboardResponse;
import com.pratham.paymentx.security.UserPrincipal;
import com.pratham.paymentx.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}