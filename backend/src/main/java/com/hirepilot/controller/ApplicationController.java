package com.hirepilot.controller;

import com.hirepilot.domain.Application;
import com.hirepilot.repository.ApplicationRepository;
import com.hirepilot.service.AutoApplyService;
import com.hirepilot.service.InterviewPrepService;
import com.hirepilot.service.MatchingEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final AutoApplyService autoApplyService;
    private final MatchingEngineService matchingEngineService;
    private final InterviewPrepService interviewPrepService;

    /**
     * GET /api/applications/user/{userId}
     * Returns all applications for the Kanban board.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ApplicationResponse>> getUserApplications(@PathVariable UUID userId) {
        List<Application> apps = applicationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(apps.stream().map(ApplicationResponse::from).toList());
    }

    /**
     * GET /api/applications/user/{userId}/kanban
     * Returns applications grouped by status for Kanban view.
     */
    @GetMapping("/user/{userId}/kanban")
    public ResponseEntity<Map<String, List<ApplicationResponse>>> getKanban(@PathVariable UUID userId) {
        List<Application> apps = applicationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, List<ApplicationResponse>> kanban = new java.util.LinkedHashMap<>();
        for (String s : List.of(
                "PENDING_CONFIRMATION", "APPLIED", "SHORTLISTED", "ASSESSMENT",
                "ROUND_1", "ROUND_1_CLEARED", "ROUND_2", "ROUND_2_CLEARED",
                "ROUND_3", "ROUND_3_CLEARED", "HR_ROUND", "WAITING_FOR_HR",
                "OFFER", "OFFER_ACCEPTED", "OFFER_DECLINED",
                "REJECTED", "GHOSTED", "WITHDRAWN", "FAILED")) {
            kanban.put(s, new java.util.ArrayList<>());
        }

        apps.stream().map(ApplicationResponse::from)
                .forEach(app -> kanban.computeIfAbsent(app.status(), k -> new java.util.ArrayList<>()).add(app));

        return ResponseEntity.ok(kanban);
    }

    /**
     * POST /api/applications/{id}/confirm
     * Human-in-the-loop: user confirms submission.
     * Body: { "confirmationToken": "..." }
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApplicationResponse> confirmApplication(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String token = body.get("confirmationToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Application updated = autoApplyService.confirmAndApply(token);
        return ResponseEntity.ok(ApplicationResponse.from(updated));
    }

    /**
     * PATCH /api/applications/{id}/status
     * Manually move a Kanban card.
     * Body: { "status": "INTERVIEW" }
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        return applicationRepository.findById(id)
                .map(app -> {
                    app.setStatus(Application.ApplicationStatus.valueOf(body.get("status")));
                    return ResponseEntity.ok(ApplicationResponse.from(applicationRepository.save(app)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PATCH /api/applications/{id}/notes
     * Save freeform notes for an application.
     */
    @PatchMapping("/{id}/notes")
    public ResponseEntity<ApplicationResponse> updateNotes(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        return applicationRepository.findById(id)
                .map(app -> {
                    app.setNotes(body.getOrDefault("notes", ""));
                    return ResponseEntity.ok(ApplicationResponse.from(applicationRepository.save(app)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/applications/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        return applicationRepository.findById(id)
                .map(a -> ResponseEntity.ok(ApplicationResponse.from(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/applications/{id}/evaluate
     * Triggers matching evaluation + queues the application pipeline for an existing job.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<String> evaluateJob(
            @RequestParam UUID userId,
            @RequestParam UUID jobId) {
        matchingEngineService.evaluateAndRoute(userId, jobId);
        return ResponseEntity.ok("Evaluation triggered");
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    public record ApplicationResponse(
            UUID id,
            UUID jobId,
            String jobTitle,
            String company,
            String jobUrl,
            String status,
            BigDecimal matchScore,
            String tailoredResumeText,
            String coverLetterText,
            String confirmationToken,
            String notes,
            String appliedAt,
            String createdAt
    ) {
        static ApplicationResponse from(Application a) {
            return new ApplicationResponse(
                    a.getId(),
                    a.getJob().getId(),
                    a.getJob().getTitle(),
                    a.getJob().getCompany(),
                    a.getJob().getUrl(),
                    a.getStatus().name(),
                    a.getMatchScore(),
                    a.getTailoredResumeText(),
                    a.getCoverLetterText(),
                    a.getConfirmationToken(),
                    a.getNotes(),
                    a.getAppliedAt() != null ? a.getAppliedAt().toString() : null,
                    a.getCreatedAt() != null ? a.getCreatedAt().toString() : null
            );
        }
    }
}
