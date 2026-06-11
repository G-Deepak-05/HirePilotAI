package com.hirepilot.controller;

import com.hirepilot.domain.PrepSheet;
import com.hirepilot.repository.PrepSheetRepository;
import com.hirepilot.service.InterviewPrepService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prep")
@RequiredArgsConstructor
public class PrepController {

    private final PrepSheetRepository prepSheetRepository;
    private final InterviewPrepService interviewPrepService;

    /**
     * GET /api/prep/application/{applicationId}
     * Returns the prep sheet for a given application.
     */
    @GetMapping("/application/{applicationId}")
    public ResponseEntity<PrepSheetResponse> getPrepSheet(@PathVariable UUID applicationId) {
        return prepSheetRepository.findByApplicationId(applicationId)
                .map(p -> ResponseEntity.ok(PrepSheetResponse.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/prep/application/{applicationId}/generate
     * Triggers prep sheet generation (or regeneration) via DeepSeek-R1.
     */
    @PostMapping("/application/{applicationId}/generate")
    public ResponseEntity<PrepSheetResponse> generatePrepSheet(@PathVariable UUID applicationId) {
        PrepSheet sheet = interviewPrepService.generatePrepSheet(applicationId);
        return ResponseEntity.ok(PrepSheetResponse.from(sheet));
    }

    // ─── DTO ──────────────────────────────────────────────────────────────────

    public record PrepSheetResponse(
            UUID id,
            UUID applicationId,
            List<String> technicalQuestions,
            List<String> systemDesignScenarios,
            List<String> behavioralQuestions,
            String companyResearch,
            String generationStatus
    ) {
        static PrepSheetResponse from(PrepSheet p) {
            return new PrepSheetResponse(
                    p.getId(),
                    p.getApplication().getId(),
                    p.getTechnicalQuestions(),
                    p.getSystemDesignScenarios(),
                    p.getBehavioralQuestions(),
                    p.getCompanyResearch(),
                    p.getGenerationStatus().name()
            );
        }
    }
}
