package com.hirepilot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(name = "jd_text", columnDefinition = "TEXT")
    private String jdText;

    private String source;

    @Column(nullable = false, unique = true, length = 2048)
    private String url;

    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    private String urlHash;

    private String location;

    @Column(name = "is_remote")
    @Builder.Default
    private Boolean isRemote = false;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "required_skills", columnDefinition = "TEXT[]")
    private String[] requiredSkills;

    @Column(name = "experience_level")
    private String experienceLevel;

    @Column(name = "qdrant_vector_id")
    private String qdrantVectorId;

    @Column(name = "scraped_at", nullable = false)
    @Builder.Default
    private OffsetDateTime scrapedAt = OffsetDateTime.now();

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
