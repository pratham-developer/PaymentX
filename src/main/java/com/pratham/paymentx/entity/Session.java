package com.pratham.paymentx.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_session_user_family", columnList = "user_id,familyId")
        }
)
@Builder
public class Session {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false)
    private String refreshTokenHash;

    //will act as the common identifier for all access tokens issued for the session
    //access tokens for a user's session = userId:sessionId:familyId
    //not unique
    @Column(nullable = false)

    private UUID familyId;

    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
