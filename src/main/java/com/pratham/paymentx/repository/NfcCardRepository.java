package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.NfcCard;
import com.pratham.paymentx.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NfcCardRepository extends JpaRepository<NfcCard, UUID> {
    boolean existsByChipId(String chipId);
    Optional<NfcCard> findByStudentProfile(StudentProfile studentProfile);
}
