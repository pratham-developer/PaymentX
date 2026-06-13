package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.MerchantGatewayStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    boolean existsByBankAccountHash(String bankAccountHash);

    @Query("SELECT mp FROM MerchantProfile mp JOIN FETCH mp.user WHERE mp.user.id = :userId")
    Optional<MerchantProfile> findByUserIdWithUser(@Param("userId") UUID userId);

    @Query(value = "SELECT mp FROM MerchantProfile mp JOIN FETCH mp.user",
            countQuery = "SELECT count(mp) FROM MerchantProfile mp")
    Page<MerchantProfile> findAllWithUser(Pageable pageable);

    @Query(value = "SELECT mp FROM MerchantProfile mp JOIN FETCH mp.user WHERE mp.gatewayStatus = :status",
            countQuery = "SELECT count(mp) FROM MerchantProfile mp WHERE mp.gatewayStatus = :status")
    Page<MerchantProfile> findByGatewayStatusWithUser(
            @Param("status") MerchantGatewayStatus status,
            Pageable pageable
    );

    @Query("SELECT m FROM MerchantProfile m JOIN FETCH m.user u JOIN Wallet w ON w.user = u " +
            "WHERE m.gatewayStatus = 'BENEFICIARY_CREATED' AND w.availableBalance >= 10.00")
    List<MerchantProfile> findEligibleMerchantsForAutoPayout();

    Page<MerchantProfile> findByGatewayStatus(
            MerchantGatewayStatus status,
            Pageable pageable
    );

}