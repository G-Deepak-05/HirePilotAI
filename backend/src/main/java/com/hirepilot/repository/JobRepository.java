package com.hirepilot.repository;

import com.hirepilot.domain.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    boolean existsByUrlHash(String urlHash);
    Optional<Job> findByUrlHash(String urlHash);
    Page<Job> findByIsActiveTrue(Pageable pageable);
    List<Job> findBySourceAndIsActiveTrue(String source);

    @Query("SELECT j FROM Job j WHERE j.isActive = true ORDER BY j.scrapedAt DESC")
    Page<Job> findRecentActiveJobs(Pageable pageable);
}
