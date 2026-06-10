package com.hirepilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirepilot.config.OllamaConfig;
import com.hirepilot.domain.Resume;
import com.hirepilot.domain.User;
import com.hirepilot.repository.ResumeRepository;
import com.hirepilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeIntelligenceService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final OllamaConfig ollamaConfig;
    private final ObjectMapper objectMapper;

    @Qualifier("ollamaWebClient")
    private final WebClient ollamaWebClient;

    private static final String PARSE_PROMPT = """
            You are a resume parsing expert. Extract the following information from this resume text into valid JSON.
            
            Return ONLY a valid JSON object with this exact structure (no markdown, no extra text):
            {
              "summary": "brief professional summary",
              "skills": ["skill1", "skill2", ...],
              "experience": [
                {
                  "company": "Company Name",
                  "title": "Job Title",
                  "startDate": "YYYY-MM",
                  "endDate": "YYYY-MM or present",
                  "durationYears": 1.5,
                  "description": "key responsibilities and achievements",
                  "technologies": ["tech1", "tech2"]
                }
              ],
              "education": [
                {
                  "institution": "University Name",
                  "degree": "Degree Type",
                  "field": "Field of Study",
                  "graduationYear": 2020
                }
              ],
              "projects": [
                {
                  "name": "Project Name",
                  "description": "what it does",
                  "technologies": ["tech1", "tech2"],
                  "url": "optional url"
                }
              ],
              "totalExperienceYears": 3.5,
              "seniorityLevel": "junior|mid|senior|staff|principal"
            }
            
            Resume text:
            %s
            """;

    /**
     * Ingests a PDF or DOCX resume, extracts raw text via Apache Tika,
     * parses it into structured JSON via Qwen3, and persists the resume.
     */
    @Transactional
    public Resume ingestResume(MultipartFile file, UUID userId) throws IOException, org.apache.tika.exception.TikaException {
        log.info("Starting resume ingestion for user {} - file: {}", userId, file.getOriginalFilename());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 1. Extract raw text with Apache Tika
        String rawText = extractText(file);
        log.debug("Extracted {} characters from resume", rawText.length());

        // 2. Persist initial record with PENDING status
        Resume resume = Resume.builder()
                .user(user)
                .originalFilename(file.getOriginalFilename())
                .rawText(rawText)
                .parsingStatus(Resume.ParsingStatus.PROCESSING)
                .build();
        resume = resumeRepository.save(resume);

        // 3. Parse via Qwen3 (synchronous for now; can be made async via Kafka)
        try {
            Map<String, Object> parsedData = parseWithOllama(rawText);
            String[] skills = extractSkillsArray(parsedData);
            BigDecimal expYears = extractExperienceYears(parsedData);

            resume.setParsedData(parsedData);
            resume.setSkills(skills);
            resume.setExperienceYears(expYears);
            resume.setParsingStatus(Resume.ParsingStatus.COMPLETED);
            log.info("Resume parsed successfully: {} skills extracted, {} years experience", skills.length, expYears);
        } catch (Exception e) {
            log.error("Failed to parse resume with Ollama", e);
            resume.setParsingStatus(Resume.ParsingStatus.FAILED);
        }

        return resumeRepository.save(resume);
    }

    private String extractText(MultipartFile file) throws IOException, org.apache.tika.exception.TikaException {
        Tika tika = new Tika();
        return tika.parseToString(file.getInputStream());
    }

    private Map<String, Object> parseWithOllama(String rawText) {
        // /no_think disables Qwen3 chain-of-thought so output goes to 'response' not 'thinking'
        String prompt = "/no_think\n" + PARSE_PROMPT.formatted(rawText);

        // NOTE: Do NOT use "format":"json" — it causes Ollama 500 when Qwen3 emits <think> tokens
        // before the JSON block. We extract JSON ourselves instead.
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model",   ollamaConfig.getJdAnalyzerModel());
        requestBody.put("prompt",  prompt);
        requestBody.put("stream",  false);
        requestBody.put("think",   false);
        // Ask model to skip thinking if supported (Qwen3 >=0.5, Ollama >=0.6)
        Map<String, Object> options = new java.util.HashMap<>();
        options.put("temperature", 0.1);
        options.put("num_predict", 4096);  // generous budget for thinking + JSON output
        requestBody.put("options", options);

        Map<String, Object> response = ollamaWebClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("Empty response from Ollama");
        }

        // Qwen3 in some Ollama builds puts content in 'thinking' when response is empty
        String rawResponse = (String) response.get("response");
        if (rawResponse == null || rawResponse.isBlank()) {
            rawResponse = (String) response.getOrDefault("thinking", "");
            log.info("Ollama: response field was empty, using 'thinking' field ({} chars)", rawResponse.length());
        }
        String jsonStr = extractJsonBlock(rawResponse);
        try {
            return objectMapper.readValue(jsonStr, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not parse Ollama JSON, returning empty map. Raw: {}", rawResponse);
            return Map.of();
        }
    }

    /**
     * Strips <think>...</think> blocks then extracts the first {...} JSON object.
     * More robust than relying on Ollama's format:json enforcement.
     */
    private String extractJsonBlock(String text) {
        if (text == null || text.isBlank()) return "{}";
        // 1. Remove thinking tokens
        String stripped = text.replaceAll("(?s)<think>.*?</think>", "").trim();
        // 2. Find first { ... } block
        int start = stripped.indexOf('{');
        int end   = stripped.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return stripped.substring(start, end + 1);
        }
        return "{}";
    }

    @SuppressWarnings("unchecked")
    private String[] extractSkillsArray(Map<String, Object> parsedData) {
        Object skills = parsedData.get("skills");
        if (skills instanceof List<?> list) {
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
        return new String[0];
    }

    private BigDecimal extractExperienceYears(Map<String, Object> parsedData) {
        Object years = parsedData.get("totalExperienceYears");
        if (years instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
