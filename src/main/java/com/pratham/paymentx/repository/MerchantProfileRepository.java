package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, UUID> {
    Optional<MerchantProfile> findByUser(User user);
    Optional<MerchantProfile> findByUserId(UUID userId);
    boolean existsByBankAccountHash(String bankAccountHash);

    @Query("SELECT mp FROM MerchantProfile mp JOIN FETCH mp.user WHERE mp.user.id = :userId")
    Optional<MerchantProfile> findByUserIdWithUser(@Param("userId") UUID userId);

}
