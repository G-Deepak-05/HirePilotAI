package com.hirepilot.controller;

import com.hirepilot.domain.Job;
import com.hirepilot.repository.JobRepository;
import com.hirepilot.service.JobScraperService;
import com.hirepilot.service.MatchingEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;
    private final JobScraperService jobScraperService;
    private final MatchingEngineService matchingEngineService;

    /**
     * GET /api/jobs?page=0&size=20
     * Returns paginated list of active jobs.
     */
    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Job> jobs = jobRepository.findByIsActiveTrue(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scrapedAt")));

        return ResponseEntity.ok(jobs.map(JobResponse::from));
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
    public ResponseEntity<String> scrapeGreenhouse(@PathVariable String companySlug) {
        jobScraperService.scrapeGreenhouseBoard(companySlug);
        return ResponseEntity.ok("Scrape initiated for: " + companySlug);
    }

    /**
     * POST /api/jobs/scrape/yc
     * Manually triggers a YC Jobs scrape.
     */
    @PostMapping("/scrape/yc")
    public ResponseEntity<String> scrapeYC() {
        jobScraperService.scrapeYCJobs();
        return ResponseEntity.ok("YC Jobs scrape initiated");
    }

    /** POST /api/jobs/scrape/remotive — remote software-dev jobs feed */
    @PostMapping("/scrape/remotive")
    public ResponseEntity<String> scrapeRemotive() {
        jobScraperService.scrapeRemotive();
        return ResponseEntity.ok("Remotive scrape initiated");
    }

    /** POST /api/jobs/scrape/remoteok — remote engineering jobs feed */
    @PostMapping("/scrape/remoteok")
    public ResponseEntity<String> scrapeRemoteOK() {
        jobScraperService.scrapeRemoteOK();
        return ResponseEntity.ok("RemoteOK scrape initiated");
    }

    /** POST /api/jobs/scrape/jobicy — remote US engineering jobs */
    @PostMapping("/scrape/jobicy")
    public ResponseEntity<String> scrapeJobicy() {
        jobScraperService.scrapeJobicy();
        return ResponseEntity.ok("Jobicy scrape initiated");
    }

    /** POST /api/jobs/scrape/all — fires all 5 sources at once */
    @PostMapping("/scrape/all")
    public ResponseEntity<String> scrapeAll() {
        jobScraperService.runDailyScrape();
        return ResponseEntity.ok("All sources scrape initiated: YC Jobs, Greenhouse (manual), Remotive, RemoteOK, Jobicy");
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
            String scrapedAt
    ) {
        static JobResponse from(Job j) {
            return new JobResponse(
                    j.getId(), j.getTitle(), j.getCompany(), j.getSource(),
                    j.getUrl(), j.getLocation(), j.getIsRemote(),
                    j.getSalaryMin(), j.getSalaryMax(),
                    j.getRequiredSkills(), j.getExperienceLevel(),
                    j.getScrapedAt() != null ? j.getScrapedAt().toString() : null
            );
        }
    }

    public record MatchScoreResponse(
            double totalScore, double skillScore,
            double experienceScore, double keywordScore, double locationScore
    ) {}
}
