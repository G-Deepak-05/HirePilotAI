package com.hirepilot.service;

import com.hirepilot.config.OllamaConfig;
import com.hirepilot.domain.Application;
import com.hirepilot.domain.PrepSheet;
import com.hirepilot.repository.ApplicationRepository;
import com.hirepilot.repository.PrepSheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewPrepService {

    private final PrepSheetRepository prepSheetRepository;
    private final ApplicationRepository applicationRepository;
    private final OllamaConfig ollamaConfig;

    @Qualifier("ollamaWebClient")
    private final WebClient ollamaWebClient;

    private static final String TECHNICAL_QUESTIONS_PROMPT = """
            You are a senior technical interviewer. Based on this job description, generate exactly 7 
            highly targeted technical interview questions. Focus on the specific technologies, patterns, 
            and problem domains mentioned in the JD.
            
            Return ONLY a JSON array of strings. Example: ["Question 1?", "Question 2?"]
            
            Job Title: %s
            Company: %s
            Job Description:
            %s
            """;

    private static final String SYSTEM_DESIGN_PROMPT = """
            You are a Staff Engineer conducting a system design interview. Based on this job description,
            generate 3 realistic system design scenarios that the company would likely ask.
            Make them specific to the company's domain and scale.
            
            Return ONLY a JSON array of strings. Example: ["Design X for Y scale", ...]
            
            Job Title: %s
            Company: %s
            Job Description:
            %s
            """;

    private static final String BEHAVIORAL_PROMPT = """
            You are an experienced hiring manager. Generate 5 targeted behavioral interview questions
            in STAR format for this role. Focus on leadership, technical decision-making, conflict 
            resolution, and growth mindset relevant to the company's engineering culture.
            
            Return ONLY a JSON array of strings. Example: ["Tell me about a time when...", ...]
            
            Job Title: %s
            Company: %s
            """;

    /**
     * Generates a complete interview prep sheet for an application.
     * Uses DeepSeek-R1 for logical reasoning and question quality.
     */
    @Transactional
    public PrepSheet generatePrepSheet(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        // Check if one already exists
        Optional<PrepSheet> existing = prepSheetRepository.findByApplicationId(applicationId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PrepSheet prepSheet = PrepSheet.builder()
                .application(application)
                .generationStatus(PrepSheet.GenerationStatus.GENERATING)
                .build();
        prepSheet = prepSheetRepository.save(prepSheet);

        String jobTitle = application.getJob().getTitle();
        String company  = application.getJob().getCompany();
        String jdText   = application.getJob().getJdText();

        try {
            List<String> technicalQuestions = generateJsonArrayResponse(
                    TECHNICAL_QUESTIONS_PROMPT.formatted(jobTitle, company, jdText));

            List<String> systemDesignScenarios = generateJsonArrayResponse(
                    SYSTEM_DESIGN_PROMPT.formatted(jobTitle, company, jdText));

            List<String> behavioralQuestions = generateJsonArrayResponse(
                    BEHAVIORAL_PROMPT.formatted(jobTitle, company));

            prepSheet.setTechnicalQuestions(technicalQuestions);
            prepSheet.setSystemDesignScenarios(systemDesignScenarios);
            prepSheet.setBehavioralQuestions(behavioralQuestions);
            prepSheet.setGenerationStatus(PrepSheet.GenerationStatus.COMPLETED);

            log.info("Prep sheet generated for application {}: {} technical, {} system design, {} behavioral",
                    applicationId, technicalQuestions.size(), systemDesignScenarios.size(), behavioralQuestions.size());

        } catch (Exception e) {
            log.error("Failed to generate prep sheet for application {}", applicationId, e);
            prepSheet.setGenerationStatus(PrepSheet.GenerationStatus.FAILED);
        }

        return prepSheetRepository.save(prepSheet);
    }

    @SuppressWarnings("unchecked")
    private List<String> generateJsonArrayResponse(String prompt) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model",  ollamaConfig.getReasoningModel());
        requestBody.put("prompt", "/no_think\n" + prompt);  // disable thinking mode
        requestBody.put("stream", false);
        requestBody.put("think",  false);
        Map<String, Object> options = new java.util.HashMap<>();
        options.put("temperature", 0.3);
        requestBody.put("options", options);

        Map<String, Object> response = ollamaWebClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null || !response.containsKey("response")) {
            return List.of("Failed to generate questions");
        }

        String rawResponse = (String) response.get("response");
        // Strip <think>...</think> blocks emitted by reasoning models
        String jsonStr = rawResponse.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (jsonStr.isEmpty()) return List.of("No questions generated");
        // Extract first [...] array block
        int start = jsonStr.indexOf('[');
        int end   = jsonStr.lastIndexOf(']');
        if (start >= 0 && end > start) {
            jsonStr = jsonStr.substring(start, end + 1);
        }
        try {
            if (jsonStr.startsWith("[")) {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(jsonStr, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            }
            // Wrapped object fallback
            Map<String, Object> wrapped = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(jsonStr, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return wrapped.values().stream()
                    .filter(v -> v instanceof List)
                    .map(v -> (List<String>) v)
                    .findFirst()
                    .orElse(List.of("No questions generated"));
        } catch (Exception e) {
            log.warn("Could not parse questions JSON, returning raw: {}", jsonStr);
            return List.of(jsonStr);
        }
    }
}
