package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.auth.TokenResponse;
import com.pratham.paymentx.entity.Session;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.repository.SessionRepository;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.security.JwtProvider;
import com.pratham.paymentx.service.SessionService;
import com.pratham.paymentx.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final HashUtil hashUtil;

    @Value("${auth.sessions-allowed:1}")
    private int SESSIONS_ALLOWED;

    @Override
    @Transactional
    public TokenResponse createSession(UUID userId) {
        log.info("Acquiring pessimistic write lock for user ID: {}", userId);

        // fetch the user by locking the row to prevent race conditions while creating sessions
        User user = userRepository.findByIdAndLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // get list of all sessions
        List<Session> sessionsListForUser = sessionRepository.findByUserOrderByLastUsedAtAsc(user);

        // remove excess sessions
        int sessionCountToRemove = sessionsListForUser.size() - SESSIONS_ALLOWED + 1;
        if (sessionCountToRemove > 0) {
            log.info("Session limit exceeded for user {}. Removing {} oldest session(s).", userId, sessionCountToRemove);
            List<Session> sessionsToDelete = sessionsListForUser.subList(0, sessionCountToRemove);
            // executes query directly on db, bypassing the persistence context
            sessionRepository.deleteAllInBatch(sessionsToDelete);

            // TODO: [REDIS BLACKLIST] Blacklist these tokens in Redis
        }

        // create new session
        UUID familyId = UUID.randomUUID();
        String accessToken = jwtProvider.generateAccessToken(user, familyId);
        String refreshToken = jwtProvider.generateRefreshToken(user, familyId);
        String refreshTokenHash = hashUtil.hash(refreshToken);

        Session newSession = Session.builder()
                .user(user)
                .refreshTokenHash(refreshTokenHash)
                .familyId(familyId)
                .lastUsedAt(LocalDateTime.now())
                .build();

        sessionRepository.save(newSession);
        log.info("Successfully created new session for user ID: {}", userId);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}