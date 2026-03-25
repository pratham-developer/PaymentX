package com.pratham.paymentx.dto.auth;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class GoogleAccount {
    private String email;
    private String googleId;
    private String displayName;
    private String hostedDomain;
}
