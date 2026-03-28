package com.pratham.paymentx.entity;

import com.pratham.paymentx.enums.NfcCardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@Builder
public class NfcCard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_profile_id", nullable = false, unique = true)
    private StudentProfile studentProfile;

    @Column(nullable = false, unique = true, length = 128, updatable = false)
    private String chipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NfcCardStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}