package com.hirepilot.controller;

import com.hirepilot.domain.Resume;
import com.hirepilot.repository.UserRepository;
import com.hirepilot.service.ResumeIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
@Slf4j
public class ResumeController {

    private final ResumeIntelligenceService resumeIntelligenceService;
    private final com.hirepilot.repository.ResumeRepository resumeRepository;

    /**
     * POST /api/resumes/upload
     * Accepts a PDF or DOCX file and a userId, runs resume intelligence pipeline.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") UUID userId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.contains("pdf") &&
                 !contentType.contains("word") &&
                 !contentType.contains("document"))) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Resume resume = resumeIntelligenceService.ingestResume(file, userId);
            return ResponseEntity.ok(ResumeResponse.from(resume));
        } catch (Exception e) {
            log.error("Failed to process resume upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/resumes/user/{userId}
     * Returns all resumes for a user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ResumeResponse>> getUserResumes(@PathVariable UUID userId) {
        List<Resume> resumes = resumeRepository.findByUserId(userId);
        return ResponseEntity.ok(resumes.stream().map(ResumeResponse::from).toList());
    }

    /**
     * GET /api/resumes/{id}
     * Returns a single resume with full parsed data.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResumeResponse> getResume(@PathVariable UUID id) {
        return resumeRepository.findById(id)
                .map(r -> ResponseEntity.ok(ResumeResponse.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Response DTOs ────────────────────────────────────────────────────────

    public record ResumeResponse(
            UUID id,
            UUID userId,
            String originalFilename,
            Object parsedData,
            String[] skills,
            double experienceYears,
            String parsingStatus,
            String createdAt
    ) {
        static ResumeResponse from(Resume r) {
            return new ResumeResponse(
                    r.getId(),
                    r.getUser().getId(),
                    r.getOriginalFilename(),
                    r.getParsedData(),
                    r.getSkills(),
                    r.getExperienceYears() != null ? r.getExperienceYears().doubleValue() : 0,
                    r.getParsingStatus().name(),
                    r.getCreatedAt() != null ? r.getCreatedAt().toString() : null
            );
        }
    }
}
