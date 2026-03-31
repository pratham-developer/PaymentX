package com.pratham.paymentx.service;

import com.pratham.paymentx.entity.Session;
import com.pratham.paymentx.projection.SessionWrapper;

import java.util.List;
import java.util.UUID;

public interface TokenBlacklistService {
    void blacklist(UUID userId, UUID sessionId, UUID familyId);
    boolean isBlacklisted(UUID userId, UUID sessionId, UUID familyId);
    void blacklistAllInBatch(List<Session> sessionsToDelete);
    void blacklistAllInBatch(UUID userId, List<SessionWrapper> sessionWrappers);
}
