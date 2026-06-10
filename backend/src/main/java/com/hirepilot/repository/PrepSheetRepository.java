package com.hirepilot.repository;

import com.hirepilot.domain.PrepSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrepSheetRepository extends JpaRepository<PrepSheet, UUID> {
    Optional<PrepSheet> findByApplicationId(UUID applicationId);
}
