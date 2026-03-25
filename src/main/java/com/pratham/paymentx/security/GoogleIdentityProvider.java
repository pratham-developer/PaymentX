package com.pratham.paymentx.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.pratham.paymentx.dto.auth.GoogleAccount;
import com.pratham.paymentx.exception.ExternalAuthenticationException;
import com.pratham.paymentx.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleIdentityProvider {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    public GoogleAccount verifyGoogleId(String unverifiedToken) {
        try {
            // cryptographic verification
            GoogleIdToken token = googleIdTokenVerifier.verify(unverifiedToken);
            if (token == null) {
                throw new InvalidTokenException("Invalid or expired Google ID token.");
            }

            GoogleIdToken.Payload payload = token.getPayload();
            // business rule validation
            validatePayload(payload);
            // data mapping
            return mapToGoogleAccount(payload);

        } catch (IllegalArgumentException e) {
            // thrown by googleIdTokenVerifier if the string isn't even a valid jwt format
            throw new InvalidTokenException("Malformed token format.");
        } catch (InvalidTokenException e) {
            // rethrow our custom validation exception
            throw e;
        } catch (Exception e) {
            // catch all
            log.error("Unexpected external error during Google Token Verification", e);
            throw new ExternalAuthenticationException("Failed to verify identity with Google", e);
        }
    }

    private void validatePayload(GoogleIdToken.Payload payload) {
        if (payload.getEmail() == null || payload.getEmail().isBlank()) {
            throw new InvalidTokenException("Google account must have an email address.");
        }
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidTokenException("Google email address must be verified.");
        }
        if (payload.getSubject() == null || payload.getSubject().isBlank()) {
            throw new InvalidTokenException("Invalid token payload: Missing Google ID.");
        }
    }

    private GoogleAccount mapToGoogleAccount(GoogleIdToken.Payload payload) {
        return GoogleAccount.builder()
                .email(payload.getEmail())
                .displayName((String) payload.get("name"))
                .googleId(payload.getSubject())
                .hostedDomain(payload.getHostedDomain())
                .build();
    }
}