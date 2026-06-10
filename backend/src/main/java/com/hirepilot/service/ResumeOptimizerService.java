package com.hirepilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirepilot.config.OllamaConfig;
import com.hirepilot.domain.Application;
import com.hirepilot.domain.Job;
import com.hirepilot.domain.Resume;
import com.hirepilot.repository.ApplicationRepository;
import com.hirepilot.repository.JobRepository;
import com.hirepilot.repository.ResumeRepository;
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
public class ResumeOptimizerService {

    private final ApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final OllamaConfig ollamaConfig;
    private final ObjectMapper objectMapper;

    @Qualifier("ollamaWebClient")
    private final WebClient ollamaWebClient;

    private static final String OPTIMIZE_PROMPT = """
            You are an elite resume writer and ATS optimization specialist.
            
            Given the candidate's base resume and the target job description below, rewrite the resume to:
            1. Mirror the exact terminology and keywords from the JD (without hallucinating experience)
            2. Transform generic bullet points into STAR-format impact statements
            3. Highlight the most relevant skills prominently
            4. Quantify achievements wherever possible
            5. Ensure all content is truthful — only amplify, never fabricate
            
            Output ONLY a JSON object with this structure:
            {
              "optimizedSummary": "tailored professional summary",
              "highlightedSkills": ["skill1", "skill2"],
              "optimizedExperience": [
                {
                  "company": "...",
                  "title": "...",
                  "startDate": "...",
                  "endDate": "...",
                  "bullets": ["• Achieved X by doing Y, resulting in Z", ...]
                }
              ],
              "tailoringNotes": "Brief explanation of changes made"
            }
            
            BASE RESUME (JSON):
            %s
            
            TARGET JOB DESCRIPTION:
            Company: %s
            Role: %s
            Description:
            %s
            """;

    private static final String COVER_LETTER_PROMPT = """
            You are a professional cover letter writer. Write a compelling, personalized cover letter for this application.
            
            Requirements:
            - 3 paragraphs: Opening hook, Value proposition (matching their needs), Closing call to action
            - Mirror the company's language and culture from the JD
            - Specific and concrete — reference actual achievements from the resume
            - Professional but human — avoid generic filler phrases
            - Max 350 words
            
            Output ONLY the cover letter text (no subject line, no address blocks).
            
            CANDIDATE RESUME (JSON):
            %s
            
            TARGET JOB:
            Company: %s
            Role: %s
            Job Description:
            %s
            """;

    /**
     * Generates a tailored resume and cover letter for a specific application.
     * Uses Llama 3.1 via Ollama for creative text generation.
     */
    @Transactional
    public Application optimizeForJob(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        application.setStatus(Application.ApplicationStatus.TAILORING);
        applicationRepository.save(application);

        Job job = application.getJob();
        Resume resume = resumeRepository
                .findTopByUserIdOrderByCreatedAtDesc(application.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("No resume found"));

        try {
            // 1. Optimize resume
            String optimizedResumeJson = generateOptimizedResume(resume, job);
            application.setTailoredResumeText(optimizedResumeJson);
            log.info("Resume optimized for application {}", applicationId);

            // 2. Generate cover letter
            String coverLetter = generateCoverLetter(resume, job);
            application.setCoverLetterText(coverLetter);
            log.info("Cover letter generated for application {}", applicationId);

            // 3. Ready for human confirmation
            application.setStatus(Application.ApplicationStatus.PENDING_CONFIRMATION);
            application.setConfirmationToken(UUID.randomUUID().toString());

        } catch (Exception e) {
            log.error("Failed to optimize application {}", applicationId, e);
            application.setStatus(Application.ApplicationStatus.FAILED);
            application.setFailureReason("Optimization failed: " + e.getMessage());
        }

        return applicationRepository.save(application);
    }

    private String generateOptimizedResume(Resume resume, Job job) {
        String resumeJson = "";
        try {
            resumeJson = objectMapper.writeValueAsString(resume.getParsedData());
        } catch (Exception e) {
            resumeJson = resume.getRawText();
        }

        String prompt = OPTIMIZE_PROMPT.formatted(
                resumeJson,
                job.getCompany(),
                job.getTitle(),
                job.getJdText()
        );

        return callOllama(ollamaConfig.getResumeWriterModel(), "/no_think\n" + prompt);
    }

    private String generateCoverLetter(Resume resume, Job job) {
        String resumeJson = "";
        try {
            resumeJson = objectMapper.writeValueAsString(resume.getParsedData());
        } catch (Exception e) {
            resumeJson = resume.getRawText();
        }

        String prompt = COVER_LETTER_PROMPT.formatted(
                resumeJson,
                job.getCompany(),
                job.getTitle(),
                job.getJdText()
        );

        return callOllama(ollamaConfig.getResumeWriterModel(), "/no_think\n" + prompt);
    }

    private String callOllama(String model, String prompt) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model",  model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("think",  false);
        Map<String, Object> opts = new java.util.HashMap<>();
        opts.put("temperature", 0.2);
        opts.put("num_predict", 4096);
        requestBody.put("options", opts);

        Map<String, Object> response = ollamaWebClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("Empty response from Ollama model: " + model);
        }

        // Qwen3 may put content in 'thinking' when response is empty
        String result = (String) response.get("response");
        if (result == null || result.isBlank()) {
            result = (String) response.getOrDefault("thinking", "");
            log.debug("Ollama response empty, using thinking field ({} chars)", result.length());
        }
        return result;
    }
}
