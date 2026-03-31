package com.pratham.paymentx.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionWrapper {
    private UUID sessionId;
    private UUID familyId;
}
