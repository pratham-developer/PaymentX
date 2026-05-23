package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, UUID> {
    Optional<MerchantProfile> findByUser(User user);
    Optional<MerchantProfile> findByUserId(UUID userId);

    @Query("SELECT mp FROM MerchantProfile mp JOIN FETCH mp.user WHERE mp.user.id = :userId")
    Optional<MerchantProfile> findByUserIdWithUser(@Param("userId") UUID userId);

    @Query("""
            SELECT mp FROM MerchantProfile mp
            JOIN FETCH mp.user u
            WHERE mp.gatewayStatus = :status
            """)
    List<MerchantProfile> findAllByGatewayStatus(@Param("status") MerchantGatewayStatus status);
}
