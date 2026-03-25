package com.pratham.paymentx.security;

import com.pratham.paymentx.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtProvider {
    @Value("${jwt.access.secret}")
    private String accessSecret;

    @Value("${jwt.refresh.secret}")
    private String refreshSecret;

    @Value("${jwt.access.expiry-ms}")
    private long accessExpiryMs;

    @Value("${jwt.refresh.expiry-ms}")
    private long refreshExpiryMs;

    private SecretKey accessKey(){
        return Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
    }
    private SecretKey refreshKey(){
        return Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }


    public String generateAccessToken(User user, String familyId){
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("familyId",familyId)
                .claim("email",user.getEmail())
                .claim("role",user.getRole().name())
                .claim("profileCompleted",user.getProfileCompleted())
                .claim("profileActive",user.getProfileActive())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiryMs))
                .signWith(accessKey())
                .compact();
    }

    public String generateRefreshToken(User user, String familyId){
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("familyId",familyId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+refreshExpiryMs))
                .signWith(refreshKey())
                .compact();
    }

    private Claims parse(String token, SecretKey secretKey){
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
