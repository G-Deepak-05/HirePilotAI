package com.hirepilot.controller;

import com.hirepilot.domain.Job;
import com.hirepilot.domain.Application;
import com.hirepilot.repository.JobRepository;
import com.hirepilot.repository.ApplicationRepository;
import com.hirepilot.repository.PrepSheetRepository;
import com.hirepilot.service.JobScraperService;
import com.hirepilot.service.MatchingEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final PrepSheetRepository prepSheetRepository;
    private final JobScraperService jobScraperService;
    private final MatchingEngineService matchingEngineService;

    /**
     * GET /api/jobs?page=0&size=20&userId=...
     * Returns paginated list of active jobs, sorted by match score descending if userId is provided.
     */
    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId) {

        if (userId == null) {
            Page<Job> jobs = jobRepository.findByIsActiveTrue(
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scrapedAt")));
            return ResponseEntity.ok(jobs.map(JobResponse::from));
        }

        // Fetch active jobs and user applications
        List<Job> activeJobs = jobRepository.findByIsActiveTrue();
        List<Application> userApps = applicationRepository.findByUserId(userId);
        
        // Map jobId -> Application for O(1) checks
        Map<UUID, Application> jobIdToAppMap = userApps.stream()
                .collect(Collectors.toMap(app -> app.getJob().getId(), app -> app, (a, b) -> a));

        List<JobResponse> enrichedJobs = activeJobs.stream()
                .map(job -> {
                    Double matchScore = null;
                    try {
                        MatchingEngineService.MatchResult scoreResult = matchingEngineService.computeMatchScore(userId, job.getId());
                        matchScore = scoreResult.totalScore().doubleValue();
                    } catch (Exception e) {
                        // Safe fallback for no resume
                    }

                    Application app = jobIdToAppMap.get(job.getId());
                    String status = app != null ? app.getStatus().name() : null;
                    UUID appId = app != null ? app.getId() : null;

                    return new JobResponse(
                            job.getId(), job.getTitle(), job.getCompany(), job.getSource(),
                            job.getUrl(), job.getLocation(), job.getIsRemote(),
                            job.getSalaryMin(), job.getSalaryMax(),
                            job.getRequiredSkills(), job.getExperienceLevel(),
                            job.getScrapedAt() != null ? job.getScrapedAt().toString() : null,
                            matchScore,
                            status,
                            appId
                    );
                })
                .sorted((a, b) -> {
                    double scoreA = a.matchScore() != null ? a.matchScore() : 0.0;
                    double scoreB = b.matchScore() != null ? b.matchScore() : 0.0;
                    return Double.compare(scoreB, scoreA);
                })
                .toList();

        int start = Math.min(page * size, enrichedJobs.size());
        int end = Math.min(start + size, enrichedJobs.size());
        List<JobResponse> subList = enrichedJobs.subList(start, end);

        PageImpl<JobResponse> pageResult = new PageImpl<>(subList, PageRequest.of(page, size), enrichedJobs.size());
        return ResponseEntity.ok(pageResult);
    }

    /**
     * DELETE /api/jobs/clear
     * Clears all jobs, applications, and prep sheets to start fresh.
     */
    @DeleteMapping("/clear")
    @Transactional
    public ResponseEntity<Map<String, String>> clearJobs() {
        prepSheetRepository.deleteAllInBatch();
        applicationRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        return ResponseEntity.ok(Map.of("message", "Job queue cleared successfully"));
    }

    /**
     * GET /api/jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .map(j -> ResponseEntity.ok(JobResponse.from(j)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/jobs/scrape/greenhouse/{companySlug}
     * Manually triggers a Greenhouse board scrape.
     */
    @PostMapping("/scrape/greenhouse/{companySlug}")
    public ResponseEntity<Map<String, String>> scrapeGreenhouse(@PathVariable String companySlug) {
        jobScraperService.scrapeGreenhouseBoard(companySlug);
        return ResponseEntity.ok(Map.of("message", "Scrape initiated for: " + companySlug));
    }

    /**
     * POST /api/jobs/scrape/yc
     * Manually triggers a YC Jobs scrape.
     */
    @PostMapping("/scrape/yc")
    public ResponseEntity<Map<String, String>> scrapeYC() {
        jobScraperService.scrapeYCJobs();
        return ResponseEntity.ok(Map.of("message", "YC Jobs scrape initiated"));
    }

    /** POST /api/jobs/scrape/remotive — remote software-dev jobs feed */
    @PostMapping("/scrape/remotive")
    public ResponseEntity<Map<String, String>> scrapeRemotive() {
        jobScraperService.scrapeRemotive();
        return ResponseEntity.ok(Map.of("message", "Remotive scrape initiated"));
    }

    /** POST /api/jobs/scrape/remoteok — remote engineering jobs feed */
    @PostMapping("/scrape/remoteok")
    public ResponseEntity<Map<String, String>> scrapeRemoteOK() {
        jobScraperService.scrapeRemoteOK();
        return ResponseEntity.ok(Map.of("message", "RemoteOK scrape initiated"));
    }

    /** POST /api/jobs/scrape/jobicy — remote US engineering jobs */
    @PostMapping("/scrape/jobicy")
    public ResponseEntity<Map<String, String>> scrapeJobicy() {
        jobScraperService.scrapeJobicy();
        return ResponseEntity.ok(Map.of("message", "Jobicy scrape initiated"));
    }

    /** POST /api/jobs/scrape/all — fires all 5 sources at once */
    @PostMapping("/scrape/all")
    public ResponseEntity<Map<String, String>> scrapeAll() {
        jobScraperService.runDailyScrape();
        return ResponseEntity.ok(Map.of("message", "All sources scrape initiated: YC Jobs, Greenhouse (manual), Remotive, RemoteOK, Jobicy"));
    }

    /**
     * POST /api/jobs/{jobId}/match/{userId}
     * Manually compute match score for a job.
     */
    @PostMapping("/{jobId}/match/{userId}")
    public ResponseEntity<MatchScoreResponse> computeMatch(
            @PathVariable UUID jobId,
            @PathVariable UUID userId) {

        MatchingEngineService.MatchResult result = matchingEngineService.computeMatchScore(userId, jobId);
        return ResponseEntity.ok(new MatchScoreResponse(
                result.totalScore().doubleValue(),
                result.skillScore(),
                result.experienceScore(),
                result.keywordScore(),
                result.locationScore()
        ));
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    public record JobResponse(
            UUID id, String title, String company, String source,
            String url, String location, Boolean isRemote,
            Integer salaryMin, Integer salaryMax,
            String[] requiredSkills, String experienceLevel,
            String scrapedAt,
            Double matchScore,
            String applicationStatus,
            UUID applicationId
    ) {
        static JobResponse from(Job j) {
            return new JobResponse(
                    j.getId(), j.getTitle(), j.getCompany(), j.getSource(),
                    j.getUrl(), j.getLocation(), j.getIsRemote(),
                    j.getSalaryMin(), j.getSalaryMax(),
                    j.getRequiredSkills(), j.getExperienceLevel(),
                    j.getScrapedAt() != null ? j.getScrapedAt().toString() : null,
                    null, null, null
            );
        }
    }

    public record MatchScoreResponse(
            double totalScore, double skillScore,
            double experienceScore, double keywordScore, double locationScore
    ) {}
}
