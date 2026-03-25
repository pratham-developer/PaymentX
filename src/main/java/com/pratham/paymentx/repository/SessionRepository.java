package com.pratham.paymentx.repository;

import com.pratham.paymentx.entity.Session;
import com.pratham.paymentx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session,Long> {
    List<Session> findByUserOrderByLastUsedAtAsc(User user);
}
