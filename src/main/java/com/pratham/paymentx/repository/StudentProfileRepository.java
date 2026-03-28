package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.StudentProfile;
import com.pratham.paymentx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByUser(User user);
}
