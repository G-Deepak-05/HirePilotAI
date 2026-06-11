package com.hirepilot.service;

import com.hirepilot.config.KafkaConfig;
import com.hirepilot.domain.Job;
import com.hirepilot.kafka.events.JobDiscoveredEvent;
import com.hirepilot.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobScraperService {

    private final JobRepository jobRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // ─── Scheduled daily scrape ───────────────────────────────────────────────

    /**
     * Runs daily at 8 AM to discover new job postings from all free sources.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void runDailyScrape() {
        log.info("Starting daily job scrape at {}", OffsetDateTime.now());
        scrapeYCJobs();
        scrapeRemotive();
        scrapeRemoteOK();
        scrapeJobicy();
        log.info("Daily scrape completed");
    }

    // ─── Source 1: YC Work at a Startup ──────────────────────────────────────

    /**
     * YC Work at a Startup — public JSON API, no auth required.
     * URL: https://www.workatastartup.com/api/company-jobs
     */
    public void scrapeYCJobs() {
        log.info("Scraping YC Jobs (using Arbeitnow as fallback)...");
        WebClient client = webClient("https://www.arbeitnow.com");
        try {
            Map<String, Object> response = client.get()
                    .uri("/api/job-board-api")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> postings = (List<Map<String, Object>>) response.getOrDefault("data", List.of());
                log.info("Arbeitnow returned {} listings", postings.size());
                postings.forEach(this::processYCJob);
            }
        } catch (Exception e) {
            log.error("Failed to scrape YC Jobs: {}", e.getMessage());
        }
    }

    private void processYCJob(Map<String, Object> raw) {
        try {
            String url = str(raw, "url");
            if (url.isBlank()) return;

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) raw.getOrDefault("tags", List.of());

            Job job = Job.builder()
                    .title(str(raw, "title", "Unknown"))
                    .company(str(raw, "company_name", "Unknown"))
                    .jdText(str(raw, "description"))
                    .source("YC_JOBS")
                    .url(url)
                    .urlHash(hashUrl(url))
                    .location(str(raw, "location", "Remote"))
                    .isRemote(Bool(raw, "remote"))
                    .requiredSkills(tags.toArray(String[]::new))
                    .build();

            saveJobIfNew(job);
        } catch (Exception e) {
            log.warn("YC job parse failed: {}", e.getMessage());
        }
    }

    // ─── Source 2: Greenhouse (company-specific) ──────────────────────────────

    /**
     * Greenhouse public board API — no auth required.
     * Call: POST /api/jobs/scrape/greenhouse/{slug}
     * Examples: stripe, airbnb, figma, notion, linear, datadog, confluent
     */
    public void scrapeGreenhouseBoard(String companySlug) {
        log.info("Scraping Greenhouse board: {}", companySlug);
        WebClient client = webClient("https://api.greenhouse.io");
        try {
            Map<String, Object> response = client.get()
                    .uri("/v1/boards/{slug}/jobs", companySlug)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.getOrDefault("jobs", List.of());
                log.info("Greenhouse[{}] returned {} listings", companySlug, jobs.size());
                
                // Fetch detail for each job to populate descriptions
                for (Map<String, Object> j : jobs) {
                    try {
                        Object jobId = j.get("id");
                        if (jobId != null) {
                            Map<String, Object> detail = client.get()
                                    .uri("/v1/boards/{slug}/jobs/{id}", companySlug, jobId)
                                    .retrieve()
                                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                    .block();
                            if (detail != null) {
                                j.put("content", detail.get("content"));
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to fetch detail for Greenhouse job {}: {}", j.get("id"), ex.getMessage());
                    }
                    processGreenhouseJob(j, companySlug);
                }
            }
        } catch (WebClientResponseException e) {
            log.error("Greenhouse[{}] HTTP {}: {}", companySlug, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Greenhouse[{}] failed: {}", companySlug, e.getMessage());
        }
    }

    private void processGreenhouseJob(Map<String, Object> raw, String companySlug) {
        try {
            String url = str(raw, "absolute_url");
            if (url.isBlank()) return;

            @SuppressWarnings("unchecked")
            Map<String, Object> location = (Map<String, Object>) raw.getOrDefault("location", Map.of());

            String locationName = str(location, "name");
            String title = str(raw, "title", "Unknown");
            boolean isRemote = locationName.toLowerCase().contains("remote") ||
                               title.toLowerCase().contains("remote");

            String htmlContent = str(raw, "content");
            String jdText = "";
            if (!htmlContent.isBlank()) {
                jdText = htmlContent.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            }

            Job job = Job.builder()
                    .title(title)
                    .company(companySlug)
                    .source("GREENHOUSE")
                    .url(url)
                    .urlHash(hashUrl(url))
                    .location(locationName)
                    .isRemote(isRemote)
                    .jdText(jdText)
                    .build();

            saveJobIfNew(job);
        } catch (Exception e) {
            log.warn("Greenhouse job parse failed: {}", e.getMessage());
        }
    }

    // ─── Source 3: Remotive ────────────────────────────────────────────────────

    /**
     * Remotive public API — no auth, free, rate limit: max 4x/day.
     * URL: https://remotive.com/api/remote-jobs
     * Response: { "jobs": [ { id, url, title, company_name, tags[], job_type,
     *             candidate_required_location, salary, description } ] }
     */
    public void scrapeRemotive() {
        log.info("Scraping Remotive...");
        WebClient client = webClient("https://remotive.com");
        try {
            // software-dev category, limit 100 per call
            Map<String, Object> response = client.get()
                    .uri("/api/remote-jobs?category=software-dev&limit=100")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.getOrDefault("jobs", List.of());
                log.info("Remotive returned {} listings", jobs.size());
                jobs.forEach(this::processRemotiveJob);
            }
        } catch (Exception e) {
            log.error("Remotive scrape failed: {}", e.getMessage());
        }
    }

    private void processRemotiveJob(Map<String, Object> raw) {
        try {
            String url = str(raw, "url");
            if (url.isBlank()) return;

            // tags[] comes as List<String>
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) raw.getOrDefault("tags", List.of());

            // Parse salary string like "$80k - $120k" into min/max
            String salaryStr = str(raw, "salary");
            long[] salary = parseSalaryRange(salaryStr);

            Job job = Job.builder()
                    .title(str(raw, "title", "Unknown"))
                    .company(str(raw, "company_name", "Unknown"))
                    .jdText(str(raw, "description"))
                    .source("REMOTIVE")
                    .url(url)
                    .urlHash(hashUrl(url))
                    .location(str(raw, "candidate_required_location", "Remote"))
                    .isRemote(true)
                    .requiredSkills((tags.size() > 10 ? tags.subList(0, 10) : tags).toArray(String[]::new))
                    .salaryMin(salary[0] > 0 ? (int) salary[0] : null)
                    .salaryMax(salary[1] > 0 ? (int) salary[1] : null)
                    .build();

            saveJobIfNew(job);
        } catch (Exception e) {
            log.warn("Remotive job parse failed: {}", e.getMessage());
        }
    }

    // ─── Source 4: RemoteOK ────────────────────────────────────────────────────

    /**
     * RemoteOK public JSON API — no auth required.
     * URL: https://remoteok.com/api
     * Response: array where first element is a legal notice object, rest are jobs.
     * Job fields: slug, id, position, company, tags[], description, location,
     *             apply_url, salary_min, salary_max, url
     */
    public void scrapeRemoteOK() {
        log.info("Scraping RemoteOK...");
        WebClient client = webClient("https://remoteok.com");
        try {
            // Filter for dev jobs specifically
            List<Map<String, Object>> results = client.get()
                    .uri("/api?tag=dev")
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .collectList()
                    .block();

            if (results == null || results.isEmpty()) return;

            // First element is the legal notice map — skip it
            List<Map<String, Object>> jobs = results.size() > 1 ? results.subList(1, results.size()) : List.of();
            log.info("RemoteOK returned {} listings", jobs.size());
            jobs.forEach(this::processRemoteOKJob);

        } catch (Exception e) {
            log.error("RemoteOK scrape failed: {}", e.getMessage());
        }
    }

    private void processRemoteOKJob(Map<String, Object> raw) {
        try {
            String url = str(raw, "url");
            if (url.isBlank()) return;

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) raw.getOrDefault("tags", List.of());

            long salaryMin = toLong(raw.get("salary_min"));
            long salaryMax = toLong(raw.get("salary_max"));

            Job job = Job.builder()
                    .title(str(raw, "position", "Unknown"))
                    .company(str(raw, "company", "Unknown"))
                    .jdText(str(raw, "description"))
                    .source("REMOTEOK")
                    .url(url)
                    .urlHash(hashUrl(url))
                    .location(str(raw, "location", "Remote"))
                    .isRemote(true)
                    .requiredSkills((tags.size() > 10 ? tags.subList(0, 10) : tags).toArray(String[]::new))
                    .salaryMin(salaryMin > 0 ? (int) salaryMin : null)
                    .salaryMax(salaryMax > 0 ? (int) salaryMax : null)
                    .build();

            saveJobIfNew(job);
        } catch (Exception e) {
            log.warn("RemoteOK job parse failed: {}", e.getMessage());
        }
    }

    // ─── Source 5: Jobicy ─────────────────────────────────────────────────────

    /**
     * Jobicy public API — no auth required, free.
     * URL: https://jobicy.com/api/v2/remote-jobs
     * Params: count (max 50), geo (usa/uk/canada/europe/worldwide), industry
     * Response: { "jobs": [ { id, url, jobTitle, companyName, jobIndustry[],
     *             jobType[], jobGeo, jobLevel, jobDescription,
     *             salaryMin, salaryMax, salaryCurrency } ] }
     */
    public void scrapeJobicy() {
        log.info("Scraping Jobicy...");
        WebClient client = webClient("https://jobicy.com");
        try {
            // Pull engineering + dev jobs from the US, up to 50
            Map<String, Object> response = client.get()
                    .uri("/api/v2/remote-jobs?count=50&geo=usa&industry=engineering")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.getOrDefault("jobs", List.of());
                log.info("Jobicy returned {} listings", jobs.size());
                jobs.forEach(this::processJobicyJob);
            }
        } catch (Exception e) {
            log.error("Jobicy scrape failed: {}", e.getMessage());
        }
    }

    private void processJobicyJob(Map<String, Object> raw) {
        try {
            String url = str(raw, "url");
            if (url.isBlank()) return;

            @SuppressWarnings("unchecked")
            List<String> industries = (List<String>) raw.getOrDefault("jobIndustry", List.of());

            long salaryMin = toLong(raw.get("salaryMin"));
            long salaryMax = toLong(raw.get("salaryMax"));

            Job job = Job.builder()
                    .title(str(raw, "jobTitle", "Unknown"))
                    .company(str(raw, "companyName", "Unknown"))
                    .jdText(str(raw, "jobDescription"))
                    .source("JOBICY")
                    .url(url)
                    .urlHash(hashUrl(url))
                    .location(str(raw, "jobGeo", "Remote"))
                    .isRemote(true)
                    .experienceLevel(str(raw, "jobLevel"))
                    .requiredSkills(industries.toArray(String[]::new))
                    .salaryMin(salaryMin > 0 ? (int) salaryMin : null)
                    .salaryMax(salaryMax > 0 ? (int) salaryMax : null)
                    .build();

            saveJobIfNew(job);
        } catch (Exception e) {
            log.warn("Jobicy job parse failed: {}", e.getMessage());
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    @Transactional
    public Job saveJobIfNew(Job job) {
        if (jobRepository.existsByUrlHash(job.getUrlHash())) {
            log.debug("Duplicate skipped: {}", job.getUrl());
            return null;
        }

        Job saved = jobRepository.save(job);
        log.info("[{}] Saved: {} @ {}", saved.getSource(), saved.getTitle(), saved.getCompany());

        kafkaTemplate.send(
            KafkaConfig.TOPIC_JOB_DISCOVERED,
            saved.getId().toString(),
            JobDiscoveredEvent.builder()
                .jobId(saved.getId().toString())
                .title(saved.getTitle())
                .company(saved.getCompany())
                .jdText(saved.getJdText())
                .source(saved.getSource())
                .build()
        );
        return saved;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private WebClient webClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB limit
                .build();
    }

    private String str(Map<String, Object> map, String key) {
        return str(map, key, "");
    }

    private String str(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        if (v == null) return defaultVal;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? defaultVal : s;
    }

    private boolean Bool(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private long toLong(Object val) {
        if (val == null) return 0;
        try { return Long.parseLong(String.valueOf(val).split("\\.")[0]); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Parses salary strings like "$80k - $120k" or "$130,900" into [min, max].
     */
    private long[] parseSalaryRange(String salary) {
        if (salary == null || salary.isBlank()) return new long[]{0, 0};
        try {
            String clean = salary.replaceAll("[^0-9k\\-\\s]", "").toLowerCase().trim();
            String[] parts = clean.split("-");
            long min = parseSalaryPart(parts[0].trim());
            long max = parts.length > 1 ? parseSalaryPart(parts[1].trim()) : min;
            return new long[]{min, max};
        } catch (Exception e) {
            return new long[]{0, 0};
        }
    }

    private long parseSalaryPart(String part) {
        if (part.endsWith("k")) return Long.parseLong(part.replace("k", "")) * 1000;
        return Long.parseLong(part.replaceAll("[^0-9]", ""));
    }

    public static String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.substring(0, 64);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(url.getBytes()).toString();
        }
    }
}
