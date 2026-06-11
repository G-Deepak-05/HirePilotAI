package com.hirepilot.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class OllamaConfig {

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.models.jd-analyzer}")
    private String jdAnalyzerModel;

    @Value("${ollama.models.resume-writer}")
    private String resumeWriterModel;

    @Value("${ollama.models.reasoning}")
    private String reasoningModel;

    @Value("${ollama.timeout-seconds:120}")
    private int timeoutSeconds;

    @Bean(name = "ollamaWebClient")
    public WebClient ollamaWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB for large LLM responses
                .build();
    }

    public String getJdAnalyzerModel()   { return jdAnalyzerModel; }
    public String getResumeWriterModel() { return resumeWriterModel; }
    public String getReasoningModel()    { return reasoningModel; }
    public int    getTimeoutSeconds()    { return timeoutSeconds; }
}
