package com.pratham.paymentx.dto.auth;

import com.pratham.paymentx.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParsedAccessToken {
    private UUID userId;
    private String email;
    private UUID familyId;
    private Role role;
}
