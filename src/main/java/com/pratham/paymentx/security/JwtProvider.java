package com.pratham.paymentx.security;

import com.pratham.paymentx.dto.auth.ParsedAccessToken;
import com.pratham.paymentx.dto.auth.ParsedRefreshToken;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
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

    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void init(){
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user, UUID familyId){
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("familyId",familyId.toString())
                .claim("email",user.getEmail())
                .claim("role",user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiryMs))
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(User user, UUID familyId){
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("familyId",familyId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+refreshExpiryMs))
                .signWith(refreshKey)
                .compact();
    }

    private Claims parse(String token, SecretKey secretKey){
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public ParsedAccessToken parseAccessToken(String accessToken){
        Claims claims = parse(accessToken,accessKey);
        return ParsedAccessToken.builder()
                .userId(UUID.fromString(claims.getSubject()))
                .email(claims.get("email", String.class))
                .familyId(UUID.fromString(claims.get("familyId", String.class)))
                .role(Role.from(claims.get("role", String.class)))
                .build();
    }

    public ParsedRefreshToken parseRefreshToken(String refreshToken){
        Claims claims = parse(refreshToken,refreshKey);
        return ParsedRefreshToken.builder()
                .userId(UUID.fromString(claims.getSubject()))
                .familyId(UUID.fromString(claims.get("familyId", String.class)))
                .build();
    }
}
