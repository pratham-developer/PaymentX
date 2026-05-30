package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.auth.ParsedRefreshToken;
import com.pratham.paymentx.dto.auth.TokenResponse;
import com.pratham.paymentx.entity.Session;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.exception.InvalidTokenException;
import com.pratham.paymentx.exception.ResourceNotFoundException;
import com.pratham.paymentx.projection.SessionWrapper;
import com.pratham.paymentx.repository.SessionRepository;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.security.JwtProvider;
import com.pratham.paymentx.service.SessionService;
import com.pratham.paymentx.service.TokenBlacklistService;
import com.pratham.paymentx.util.HashUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final HashUtil hashUtil;
    private final TokenBlacklistService tokenBlacklistService;

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
            tokenBlacklistService.blacklistAllInBatch(sessionsToDelete); // Redis first
            // executes query directly on db, bypassing the persistence context
            sessionRepository.deleteAllInBatch(sessionsToDelete);        // then DB
        }

        // create new session
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String accessToken = jwtProvider.generateAccessToken(user, sessionId, familyId);
        String refreshToken = jwtProvider.generateRefreshToken(user, sessionId);
        String refreshTokenHash = hashUtil.hash(refreshToken);

        Session newSession = Session.builder()
                .id(sessionId)
                .user(user)
                .refreshTokenHash(refreshTokenHash)
                .familyId(familyId)
                .lastUsedAt(OffsetDateTime.now())
                .build();

        sessionRepository.save(newSession);
        log.info("Successfully created new session for user ID: {}", userId);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    @Transactional
    public Optional<TokenResponse> refreshSession(String refreshToken) {
        //parse refresh token
        ParsedRefreshToken parsedRefreshToken = jwtProvider.parseRefreshToken(refreshToken);
        UUID sessionId = parsedRefreshToken.getSessionId();
        UUID userId = parsedRefreshToken.getUserId();

        //find session with sessionId and userId
        Session session = sessionRepository.findSessionForRefresh(sessionId,userId).orElseThrow(
                ()->new InvalidTokenException("Refresh Token is invalid")
        );

        //hash incoming token
        String refreshTokenHash = hashUtil.hash(refreshToken);

        //if not matching then it means old token is used
        //because inside token, sessionId and userId are same throughout the session
        //actually they are logically bound to be same for the same session
        if (!refreshTokenHash.equals(session.getRefreshTokenHash())) {
            boolean withinGrace = session.getLastUsedAt().isAfter(OffsetDateTime.now().minusSeconds(5));
            if (withinGrace) {
                // Likely a legitimate network retry — the real token was just rotated.
                // Log it for visibility but don't nuke the session.
                log.warn("Refresh token hash mismatch within grace window for userId={} sessionId={}. " +
                        "Possible network retry.", userId, sessionId);
            } else {
                // Hash mismatch outside grace window = old token presented = reuse attack.
                log.warn("SECURITY: Refresh token reuse detected for userId={} sessionId={}. " +
                        "Invalidating all sessions.", userId, sessionId);
                // Redis first, then DB (per fix #1)
                List<SessionWrapper> sessionWrappers = sessionRepository.findSessionIdAndFamilyId(userId);
                tokenBlacklistService.blacklistAllInBatch(userId, sessionWrappers);
                sessionRepository.deleteAllSessionsForUser(userId);
            }
            return Optional.empty();
        }
        //get old familyId to revoke old access tokens
        UUID oldFamilyId = session.getFamilyId();

        //if match then issue new tokens
        UUID newFamilyId = UUID.randomUUID();
        String newRefreshToken = jwtProvider.generateRefreshToken(session.getUser(),sessionId);
        String newAccessToken = jwtProvider.generateAccessToken(session.getUser(),sessionId,newFamilyId);


        tokenBlacklistService.blacklist(userId, sessionId, oldFamilyId); // Redis first

        session.setFamilyId(newFamilyId);  //then save to db
        session.setRefreshTokenHash(hashUtil.hash(newRefreshToken));
        session.setLastUsedAt(OffsetDateTime.now());
        sessionRepository.saveAndFlush(session);

        TokenResponse tokenResponse = TokenResponse.builder()
                .refreshToken(newRefreshToken)
                .accessToken(newAccessToken)
                .build();

        return Optional.of(tokenResponse);
    }

    @Override
    @Transactional
    public void revokeSession(String refreshToken) {
        try {
            ParsedRefreshToken parsedRefreshToken = jwtProvider.parseRefreshToken(refreshToken);
            UUID userId = parsedRefreshToken.getUserId();
            UUID sessionId = parsedRefreshToken.getSessionId();

            Optional<Session> optional = sessionRepository.findByIdAndUserId(sessionId, userId);

            if(optional.isEmpty()){
                return;
            }
            Session session = optional.get();
            UUID familyId = session.getFamilyId(); // capture before session is modified
            tokenBlacklistService.blacklist(userId, sessionId, familyId); // Redis first
            sessionRepository.delete(session);
            sessionRepository.flush();
        } catch (JwtException e) {
            // The token is already dead. Ignore the error so the frontend clears its state.
            log.info("Logout attempted with invalid or expired token. Treating as success.");
        }
    }
}