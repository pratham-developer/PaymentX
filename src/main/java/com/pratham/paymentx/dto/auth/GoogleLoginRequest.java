package com.pratham.paymentx.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}