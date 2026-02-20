package com.pratham.paymentx.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false)
    private String refreshTokenHash;

    @Column(nullable = false)
    private String deviceFingerprint;

    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
