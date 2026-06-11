package com.hirepilot.kafka.events;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDiscoveredEvent {
    private String jobId;
    private String title;
    private String company;
    private String jdText;
    private String source;
}
