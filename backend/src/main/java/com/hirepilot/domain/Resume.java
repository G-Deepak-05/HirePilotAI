package com.hirepilot.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    /**
     * Structured JSON produced by Qwen3:
     * { skills: [], experience: [], projects: [], education: [], summary: "" }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_data", columnDefinition = "jsonb")
    private Map<String, Object> parsedData;

    @Column(name = "skills", columnDefinition = "TEXT[]")
    private String[] skills;

    @Column(name = "experience_years")
    private BigDecimal experienceYears;

    @Column(name = "qdrant_vector_id")
    private String qdrantVectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "parsing_status")
    @Builder.Default
    private ParsingStatus parsingStatus = ParsingStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ParsingStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
