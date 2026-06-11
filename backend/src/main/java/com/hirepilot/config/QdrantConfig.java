package com.hirepilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.context.annotation.Bean;

@Configuration
public class QdrantConfig {

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.port}")
    private int port;

    @Value("${qdrant.collections.resumes}")
    private String resumesCollection;

    @Value("${qdrant.collections.jobs}")
    private String jobsCollection;

    @Bean(name = "qdrantWebClient")
    public WebClient qdrantWebClient() {
        return WebClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .build();
    }

    public String getResumesCollection() { return resumesCollection; }
    public String getJobsCollection()    { return jobsCollection; }
}
