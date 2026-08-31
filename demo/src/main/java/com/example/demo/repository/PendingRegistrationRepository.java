package com.example.demo.repository;

import com.example.demo.model.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByPhone(String phone);

    /**
     * Bulk delete — NOT a derived delete. Derived {@code deleteByXxx} methods
     * load the entities and defer {@code em.remove()} to flush, so an INSERT
     * of the same unique key earlier in the same transaction collides
     * (PITFALLS 3.13). This executes the DELETE immediately.
     */
    @Modifying
    @Query("delete from PendingRegistration p where p.phone = :phone")
    void deleteByPhone(@Param("phone") String phone);
}
