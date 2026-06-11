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
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.QUEUED;

    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    @Column(name = "tailored_resume_path", length = 1024)
    private String tailoredResumePath;

    @Column(name = "cover_letter_path", length = 1024)
    private String coverLetterPath;

    @Column(name = "tailored_resume_text", columnDefinition = "TEXT")
    private String tailoredResumeText;

    @Column(name = "cover_letter_text", columnDefinition = "TEXT")
    private String coverLetterText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "screening_answers", columnDefinition = "jsonb")
    private Map<String, String> screeningAnswers;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "confirmation_token", length = 255)
    private String confirmationToken;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ApplicationStatus {
        // ── Autonomous pipeline (system-managed) ──────────────────────────
        QUEUED,
        TAILORING,
        PENDING_CONFIRMATION,
        APPLYING,
        FAILED,

        // ── Post-apply: human-managed ──────────────────────────────────────
        APPLIED,           // Submitted, awaiting response
        SHORTLISTED,       // Recruiter reached out / moved forward
        ASSESSMENT,        // Take-home / online assessment
        ROUND_1,           // 1st interview round
        ROUND_1_CLEARED,   // Passed 1st round
        ROUND_2,           // 2nd interview round
        ROUND_2_CLEARED,   // Passed 2nd round
        ROUND_3,           // 3rd interview round / final technical
        ROUND_3_CLEARED,   // Passed final round
        HR_ROUND,          // HR / culture fit round
        WAITING_FOR_HR,    // Waiting for HR callback / decision
        OFFER,             // Offer received
        OFFER_ACCEPTED,    // Accepted the offer
        OFFER_DECLINED,    // Declined the offer
        REJECTED,          // Rejected at any stage
        GHOSTED,           // No response after follow-ups
        WITHDRAWN          // Candidate withdrew
    }
}
