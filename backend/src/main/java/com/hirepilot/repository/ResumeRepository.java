package com.hirepilot.repository;

import com.hirepilot.domain.Resume;
import com.hirepilot.domain.Resume.ParsingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    List<Resume> findByUserId(UUID userId);
    Optional<Resume> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Resume> findByParsingStatus(ParsingStatus status);
}
