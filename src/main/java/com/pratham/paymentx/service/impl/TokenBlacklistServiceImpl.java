package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.entity.Session;
import com.pratham.paymentx.projection.SessionWrapper;
import com.pratham.paymentx.service.TokenBlacklistService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.access.expiry-ms}")
    private long accessExpiryMs;

    private String getKey(UUID userId, UUID sessionId, UUID familyId){
        return "jwt:blacklist:" + userId + ":" + sessionId + ":" + familyId;
    }

    @Override
    public void blacklist(UUID userId, UUID sessionId, UUID familyId) {
        try{
            String key = getKey(userId,sessionId,familyId);
            stringRedisTemplate.opsForValue()
                    .set(key, "1", accessExpiryMs, TimeUnit.MILLISECONDS);
        }catch (Exception e){
            throw new RuntimeException("Token Blacklist Operation Failed", e);
        }
    }

    @Override
    public boolean isBlacklisted(UUID userId, UUID sessionId, UUID familyId) {
        try{
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(getKey(userId,sessionId,familyId)));
        }catch (Exception e){
            throw new RuntimeException("Token Blacklist Check Failed", e);
        }
    }

    @Override
    public void blacklistAllInBatch(List<Session> sessionsToDelete) {
        if(sessionsToDelete==null || sessionsToDelete.isEmpty()){
            return;
        }
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(@NonNull RedisOperations operations) throws DataAccessException {
                    RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;
                    ValueOperations<String, String> ops = stringOps.opsForValue();

                    for (Session s : sessionsToDelete) {
                        String key = getKey(s.getUser().getId(), s.getId(), s.getFamilyId());
                        ops.set(key, "1", accessExpiryMs, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Batch Token Blacklist failed", e);
        }
    }

    @Override
    public void blacklistAllInBatch(UUID userId, List<SessionWrapper> sessionWrappers) {
        if(userId==null || sessionWrappers==null || sessionWrappers.isEmpty()){
            return;
        }
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(@NonNull RedisOperations operations) throws DataAccessException {
                    RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;
                    ValueOperations<String, String> ops = stringOps.opsForValue();

                    for (SessionWrapper wrapper : sessionWrappers) {
                        String key = getKey(userId, wrapper.getSessionId(), wrapper.getFamilyId());
                        ops.set(key, "1", accessExpiryMs, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Batch Token Blacklist failed", e);
        }
    }
}