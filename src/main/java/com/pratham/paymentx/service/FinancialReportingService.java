package com.pratham.paymentx.service;

import java.time.LocalDate;

public interface FinancialReportingService {
    void generateAndSendAdminEodReport(LocalDate targetDate);
}