package com.pratham.paymentx.controller;

import com.pratham.paymentx.dto.admin.CreateAdminRequest;
import com.pratham.paymentx.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    public ResponseEntity<Void> createAdmin(@Valid @RequestBody CreateAdminRequest request){
        log.info("Attempting to create admin with email: {}",request.getEmail());
        adminService.createAdmin(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merchant/{merchantId}/approve")
    public ResponseEntity<Void> approveMerchant(@PathVariable UUID merchantId) {
        adminService.approveMerchant(merchantId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
