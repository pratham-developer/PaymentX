package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u.id FROM User u where u.googleId = :googleId")
    Optional<UUID> findIdByGoogleId(@Param("googleId") String googleId);

    @Query("select u.id FROM User u where u.email = :email")
    Optional<UUID> findIdByEmail(@Param("email") String email);

    Optional<User> findByEmail(String email);

    //find with pessimistic write lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")
    }) //instant timeout if row is already locked
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdAndLock(@Param("userId") UUID userId);
}
