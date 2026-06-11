package com.hirepilot.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_JOB_DISCOVERED  = "job-discovered";
    public static final String TOPIC_JOB_MATCHED     = "job-matched";
    public static final String TOPIC_APP_SUBMITTED   = "application-submitted";
    public static final String TOPIC_APP_RESULT      = "application-result";

    @Bean
    public NewTopic jobDiscoveredTopic() {
        return TopicBuilder.name(TOPIC_JOB_DISCOVERED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobMatchedTopic() {
        return TopicBuilder.name(TOPIC_JOB_MATCHED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic applicationSubmittedTopic() {
        return TopicBuilder.name(TOPIC_APP_SUBMITTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic applicationResultTopic() {
        return TopicBuilder.name(TOPIC_APP_RESULT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
