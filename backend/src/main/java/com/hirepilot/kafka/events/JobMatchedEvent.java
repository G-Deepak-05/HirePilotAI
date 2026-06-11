package com.hirepilot.kafka.events;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchedEvent {
    private String applicationId;
    private String jobId;
    private String userId;
    private double matchScore;
}
