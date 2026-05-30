package com.pratham.paymentx.service;

import com.pratham.paymentx.dto.dashboard.DashboardResponse;
import java.util.UUID;

public interface DashboardService {
    DashboardResponse getDashboard(UUID userId);
}