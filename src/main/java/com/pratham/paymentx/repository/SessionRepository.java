package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.Session;
import com.pratham.paymentx.entity.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session,UUID> {
    List<Session> findByUserOrderByLastUsedAtAsc(User user);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")
    })
    @Query("""
select s from Session s join fetch s.user u
where s.id = :sessionId
AND u.id = :userId
""")
    Optional<Session> findSessionForRefresh(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId
    );


    @Modifying(flushAutomatically = true,clearAutomatically = true)
    @Query("delete from Session s where s.user.id = :userId")
    void deleteAllSessionsForUser(@Param("userId") UUID userId);


    @Query("select s from Session s where s.id = :sessionId and s.user.id = :userId")
    Optional<Session> findByIdAndUserId(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId
    );
}
