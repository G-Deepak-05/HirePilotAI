package com.hirepilot.repository;

import com.hirepilot.domain.Application;
import com.hirepilot.domain.Application.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    List<Application> findByUserId(UUID userId);
    List<Application> findByUserIdAndStatus(UUID userId, ApplicationStatus status);
    Optional<Application> findByConfirmationToken(String token);

    @Query("SELECT a FROM Application a WHERE a.user.id = :userId ORDER BY a.createdAt DESC")
    List<Application> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT COUNT(a) FROM Application a WHERE a.user.id = :userId AND a.status = :status")
    long countByUserIdAndStatus(UUID userId, ApplicationStatus status);
}
