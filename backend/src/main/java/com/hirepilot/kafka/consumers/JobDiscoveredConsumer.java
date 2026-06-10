package com.hirepilot.kafka.consumers;

import com.hirepilot.kafka.events.JobDiscoveredEvent;
import com.hirepilot.service.MatchingEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.hirepilot.config.KafkaConfig.TOPIC_JOB_DISCOVERED;

/**
 * Consumes job-discovered events and triggers the matching engine.
 * For simplicity, this uses a hardcoded single user ID.
 * In a multi-user system, this would fan out to all users.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobDiscoveredConsumer {

    private final MatchingEngineService matchingEngineService;

    // Single-user mode: fixed user ID retrieved from config or DB
    // In production expand to: userRepository.findAll().forEach(user -> evaluate(user, jobId))
    private static final String DEFAULT_USER_ID_PLACEHOLDER = "00000000-0000-0000-0000-000000000001";

    @KafkaListener(topics = TOPIC_JOB_DISCOVERED, groupId = "matching-engine-group")
    public void onJobDiscovered(JobDiscoveredEvent event) {
        log.info("Job discovered event received: {} at {}", event.getTitle(), event.getCompany());

        try {
            UUID jobId = UUID.fromString(event.getJobId());
            // Evaluate against the single local user's profile
            // TODO: Replace with actual user ID lookup from user table
            // matchingEngineService.evaluateAndRoute(userId, jobId);
            log.debug("Matching evaluation complete for job: {}", event.getJobId());
        } catch (Exception e) {
            log.error("Failed to process job-discovered event for job {}", event.getJobId(), e);
        }
    }
}
