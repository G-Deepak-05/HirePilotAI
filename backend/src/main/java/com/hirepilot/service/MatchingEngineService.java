package com.hirepilot.service;

import com.hirepilot.config.KafkaConfig;
import com.hirepilot.config.QdrantConfig;
import com.hirepilot.domain.Application;
import com.hirepilot.domain.Job;
import com.hirepilot.domain.Resume;
import com.hirepilot.domain.User;
import com.hirepilot.kafka.events.JobMatchedEvent;
import com.hirepilot.repository.ApplicationRepository;
import com.hirepilot.repository.JobRepository;
import com.hirepilot.repository.ResumeRepository;
import com.hirepilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingEngineService {

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final QdrantConfig qdrantConfig;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Qualifier("qdrantWebClient")
    private final WebClient qdrantWebClient;

    @Value("${hirepilot.matching.auto-apply-threshold}")
    private double autoApplyThreshold;

    @Value("${hirepilot.matching.manual-review-threshold}")
    private double manualReviewThreshold;

    @Value("${hirepilot.matching.weights.skills}")
    private double weightSkills;

    @Value("${hirepilot.matching.weights.experience}")
    private double weightExperience;

    @Value("${hirepilot.matching.weights.keywords}")
    private double weightKeywords;

    @Value("${hirepilot.matching.weights.location}")
    private double weightLocation;

    /**
     * Calculates match score between a user's latest resume and a job.
     * Algorithm: (Skills×0.4) + (Experience×0.3) + (Keywords×0.2) + (Location×0.1)
     * All component scores are in range [0, 100].
     *
     * @return MatchResult with total score and breakdown
     */
    public MatchResult computeMatchScore(UUID userId, UUID jobId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Resume resume = resumeRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new IllegalArgumentException("No resume found for user"));

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));

        double skillScore      = computeSkillScore(resume, job);
        double experienceScore = computeExperienceScore(resume, job);
        double keywordScore    = computeKeywordScore(resume, job);
        double locationScore   = computeLocationScore(user, job);

        double total = (skillScore * weightSkills)
                     + (experienceScore * weightExperience)
                     + (keywordScore * weightKeywords)
                     + (locationScore * weightLocation);

        BigDecimal rounded = BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);

        log.info("Match score for job {} / user {}: {} (skills={}, exp={}, kw={}, loc={})",
                jobId, userId, rounded, skillScore, experienceScore, keywordScore, locationScore);

        return new MatchResult(rounded, skillScore, experienceScore, keywordScore, locationScore);
    }

    /**
     * Evaluates a job and routes it through the pipeline:
     * >80% → QUEUED application + job-matched Kafka event
     * 60-79% → logged for manual review
     * <60% → discarded
     */
    @Transactional
    public void evaluateAndRoute(UUID userId, UUID jobId) {
        MatchResult result = computeMatchScore(userId, jobId);
        double score = result.totalScore().doubleValue();

        if (score >= autoApplyThreshold) {
            log.info("Job {} scored {}% — queueing for auto-apply", jobId, score);
            createQueuedApplication(userId, jobId, result);
        } else if (score >= manualReviewThreshold) {
            log.info("Job {} scored {}% — flagged for manual review", jobId, score);
            // Future: surface in dashboard for user decision
        } else {
            log.debug("Job {} scored {}% — below threshold, discarding", jobId, score);
        }
    }

    @Transactional
    public void forceQueueApplication(UUID userId, UUID jobId) {
        Optional<Application> existing = applicationRepository.findByUserIdAndJobId(userId, jobId);
        if (existing.isPresent()) {
            log.info("Application already exists for user {} and job {}", userId, jobId);
            return;
        }
        MatchResult result = computeMatchScore(userId, jobId);
        createQueuedApplication(userId, jobId, result);
    }

    private void createQueuedApplication(UUID userId, UUID jobId, MatchResult result) {
        User user = userRepository.getReferenceById(userId);
        Job job   = jobRepository.getReferenceById(jobId);

        Application application = Application.builder()
                .user(user)
                .job(job)
                .matchScore(result.totalScore())
                .status(Application.ApplicationStatus.QUEUED)
                .build();

        Application saved = applicationRepository.save(application);

        // Publish to Kafka for the Resume Optimizer to pick up
        JobMatchedEvent event = JobMatchedEvent.builder()
                .applicationId(saved.getId().toString())
                .jobId(jobId.toString())
                .userId(userId.toString())
                .matchScore(result.totalScore().doubleValue())
                .build();

        kafkaTemplate.send(KafkaConfig.TOPIC_JOB_MATCHED, saved.getId().toString(), event);
    }

    // ─── Score Component Computations ────────────────────────────────────────

    private double computeSkillScore(Resume resume, Job job) {
        if (resume.getSkills() == null || resume.getSkills().length == 0) return 50.0;

        Set<String> resumeSkills = new HashSet<>();
        for (String s : resume.getSkills()) {
            resumeSkills.add(s.trim());
        }

        Set<String> jobSkills = new HashSet<>();
        if (job.getRequiredSkills() != null) {
            for (String s : job.getRequiredSkills()) {
                jobSkills.add(s.trim());
            }
        }

        // Scan the job description for resume skills
        Set<String> jdSkillsFound = new HashSet<>();
        if (job.getJdText() != null && !job.getJdText().isBlank()) {
            String jdLower = job.getJdText().toLowerCase();
            for (String skill : resumeSkills) {
                String skillLower = skill.toLowerCase();
                if (jdLower.contains(skillLower)) {
                    jdSkillsFound.add(skill);
                }
            }
        }

        // Clean out generic or empty skills from jobSkills list
        jobSkills.removeIf(s -> s.equalsIgnoreCase("Software Engineering") || s.equalsIgnoreCase("Development") || s.isBlank());

        if (jobSkills.isEmpty() && jdSkillsFound.isEmpty()) {
            return 50.0; // neutral
        }

        double skillMatchRatio = 0.0;
        if (!jobSkills.isEmpty()) {
            long matches = jobSkills.stream()
                    .filter(js -> resumeSkills.stream().anyMatch(rs ->
                        rs.equalsIgnoreCase(js) ||
                        rs.toLowerCase().contains(js.toLowerCase()) ||
                        js.toLowerCase().contains(rs.toLowerCase())
                    ))
                    .count();
            skillMatchRatio = (double) matches / jobSkills.size();
        }

        // If candidate matches e.g. 5 or more skills found in JD, they get a high score
        double jdSkillsScore = jdSkillsFound.isEmpty() ? 0.0 : Math.min(100.0, jdSkillsFound.size() * 20.0);

        if (!jobSkills.isEmpty() && !jdSkillsFound.isEmpty()) {
            return (skillMatchRatio * 100.0 * 0.6) + (jdSkillsScore * 0.4);
        } else if (!jobSkills.isEmpty()) {
            return skillMatchRatio * 100.0;
        } else {
            return jdSkillsScore;
        }
    }

    private double computeExperienceScore(Resume resume, Job job) {
        if (resume.getExperienceYears() == null) return 50.0;

        double candidateYears = resume.getExperienceYears().doubleValue();
        String level = job.getExperienceLevel();

        if (level == null) return 70.0;

        return switch (level.toLowerCase()) {
            case "junior", "entry"        -> candidateYears >= 0 && candidateYears <= 3 ? 100.0 : 60.0;
            case "mid", "intermediate"    -> candidateYears >= 2 && candidateYears <= 6 ? 100.0 : 65.0;
            case "senior"                 -> candidateYears >= 5 ? 100.0 : 50.0;
            case "staff", "principal"     -> candidateYears >= 8 ? 100.0 : 40.0;
            default -> 70.0;
        };
    }

    private double computeKeywordScore(Resume resume, Job job) {
        if (resume.getRawText() == null || job.getJdText() == null) return 50.0;

        String resumeLower = resume.getRawText().toLowerCase();
        String jdLower = job.getJdText().toLowerCase();

        // Extract significant keywords from JD
        String[] jdWords = jdLower.split("[^a-zA-Z0-9+#.]+");
        long totalWords = Arrays.stream(jdWords)
                .filter(w -> w.length() > 4)
                .distinct()
                .count();

        if (totalWords == 0) return 50.0;

        long matchedWords = Arrays.stream(jdWords)
                .filter(w -> w.length() > 4)
                .distinct()
                .filter(resumeLower::contains)
                .count();

        double ratio = (double) matchedWords / totalWords;
        // Since resumes are much shorter, overlap of 30% is a near-perfect match
        return Math.min(100.0, (ratio / 0.3) * 100.0);
    }

    private double computeLocationScore(User user, Job job) {
        if (job.getIsRemote() != null && job.getIsRemote()) return 100.0; // Remote always matches
        if (user.getTargetLocations() == null || user.getTargetLocations().length == 0) return 70.0;
        if (job.getLocation() == null) return 60.0;

        String jobLoc = job.getLocation().toLowerCase();
        boolean locationMatch = Arrays.stream(user.getTargetLocations())
                .anyMatch(loc -> jobLoc.contains(loc.toLowerCase()) || loc.toLowerCase().contains(jobLoc));

        return locationMatch ? 100.0 : 20.0;
    }

    // ─── DTOs ────────────────────────────────────────────────────────────────

    public record MatchResult(
            BigDecimal totalScore,
            double skillScore,
            double experienceScore,
            double keywordScore,
            double locationScore
    ) {}
}
