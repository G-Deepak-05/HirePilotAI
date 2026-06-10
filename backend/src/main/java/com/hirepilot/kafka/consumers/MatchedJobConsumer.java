package com.hirepilot.kafka.consumers;

import com.hirepilot.kafka.events.JobMatchedEvent;
import com.hirepilot.service.ResumeOptimizerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.hirepilot.config.KafkaConfig.TOPIC_JOB_MATCHED;

/**
 * Consumes job-matched events and triggers the Resume Optimizer.
 * After optimization completes, the application moves to PENDING_CONFIRMATION.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MatchedJobConsumer {

    private final ResumeOptimizerService resumeOptimizerService;

    @KafkaListener(topics = TOPIC_JOB_MATCHED, groupId = "optimizer-group")
    public void onJobMatched(JobMatchedEvent event) {
        log.info("Job matched event received: application={}, score={}%",
                event.getApplicationId(), event.getMatchScore());

        try {
            UUID applicationId = UUID.fromString(event.getApplicationId());
            resumeOptimizerService.optimizeForJob(applicationId);
            log.info("Resume optimization complete for application {}", event.getApplicationId());
        } catch (Exception e) {
            log.error("Failed to optimize application {}", event.getApplicationId(), e);
        }
    }
}
