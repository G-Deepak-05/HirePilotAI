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
    private final com.hirepilot.repository.UserRepository userRepository;

    @KafkaListener(topics = TOPIC_JOB_DISCOVERED, groupId = "matching-engine-group")
    public void onJobDiscovered(JobDiscoveredEvent event) {
        log.info("Job discovered event received: {} at {}", event.getTitle(), event.getCompany());

        try {
            UUID jobId = UUID.fromString(event.getJobId());
            // Evaluate against all users in the system (e.g. onboarded user or placeholder)
            userRepository.findAll().forEach(user -> {
                try {
                    matchingEngineService.evaluateAndRoute(user.getId(), jobId);
                } catch (Exception ex) {
                    log.error("Failed to evaluate job {} for user {}", jobId, user.getId(), ex);
                }
            });
            log.debug("Matching evaluation complete for job: {}", event.getJobId());
        } catch (Exception e) {
            log.error("Failed to process job-discovered event for job {}", event.getJobId(), e);
        }
    }
}
