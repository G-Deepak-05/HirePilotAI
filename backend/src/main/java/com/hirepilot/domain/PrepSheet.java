package com.hirepilot.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "prep_sheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrepSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technical_questions", columnDefinition = "jsonb")
    private List<String> technicalQuestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "system_design_scenarios", columnDefinition = "jsonb")
    private List<String> systemDesignScenarios;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "behavioral_questions", columnDefinition = "jsonb")
    private List<String> behavioralQuestions;

    @Column(name = "company_research", columnDefinition = "TEXT")
    private String companyResearch;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status")
    @Builder.Default
    private GenerationStatus generationStatus = GenerationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum GenerationStatus {
        PENDING, GENERATING, COMPLETED, FAILED
    }
}
