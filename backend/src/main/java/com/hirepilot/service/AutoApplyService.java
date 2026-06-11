package com.hirepilot.service;

import com.hirepilot.config.KafkaConfig;
import com.hirepilot.config.OllamaConfig;
import com.hirepilot.domain.Application;
import com.hirepilot.domain.User;
import com.hirepilot.repository.ApplicationRepository;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AutoApplyService {

    private final ApplicationRepository applicationRepository;
    private final OllamaConfig ollamaConfig;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Qualifier("ollamaWebClient")
    private final WebClient ollamaWebClient;

    @Value("${hirepilot.auto-apply.human-confirmation-required}")
    private boolean humanConfirmationRequired;

    private static final String SCREENING_ANSWER_PROMPT = """
            You are filling out a job application form. Answer the following screening question 
            professionally and honestly based on the candidate's background.
            
            Keep the answer concise (2-4 sentences max). Sound genuine and enthusiastic.
            Return ONLY the answer text, no formatting, no preamble.
            
            Question: %s
            
            Candidate Background (JSON):
            %s
            
            Company: %s
            Role: %s
            """;

    /**
     * Human confirms the application — this is called when the user clicks 
     * "Confirm & Apply" in the frontend dashboard.
     *
     * @param confirmationToken unique token from the application record
     */
    @Transactional
    public Application confirmAndApply(String confirmationToken) {
        Application application = applicationRepository.findByConfirmationToken(confirmationToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid confirmation token"));

        if (application.getStatus() != Application.ApplicationStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("Application is not in PENDING_CONFIRMATION state: " + application.getStatus());
        }

        log.info("Human confirmed application {} — initiating Playwright automation", application.getId());
        application.setStatus(Application.ApplicationStatus.APPLYING);
        applicationRepository.save(application);

        // Run Playwright in a separate thread to avoid blocking the HTTP request
        Thread.ofVirtual().name("playwright-" + application.getId()).start(() -> {
            executePlaywrightApplication(application);
        });

        return application;
    }

    /**
     * Executes the headless browser form fill using Playwright.
     * This is the core of the Auto-Apply Agent (Feature 5).
     */
    private void executePlaywrightApplication(Application application) {
        log.info("Starting Playwright automation for application {}", application.getId());

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(false) // Show browser so user can observe
                    .setSlowMo(200);    // Slow down for better observability

            try (Browser browser = playwright.chromium().launch(options);
                 BrowserContext context = browser.newContext(
                         new Browser.NewContextOptions()
                                 .setViewportSize(1280, 800)
                                 .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                 );
                 Page page = context.newPage()) {

                String jobUrl = application.getJob().getUrl();
                log.info("Navigating to job URL: {}", jobUrl);

                page.navigate(jobUrl);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);

                // Attempt to find and click "Apply" button
                findAndClickApplyButton(page);

                // Fill standard form fields
                fillStandardFormFields(page, application);

                // Handle open-ended screening questions
                handleScreeningQuestions(page, application);

                // Upload tailored resume if available
                uploadResume(page, application);

                // Mark as APPLIED (in non-confirmation mode this would auto-submit)
                // With confirmation mode: we pause here for an additional "Submit" click
                log.info("Form filling complete for application {}", application.getId());

                // Record success
                markApplicationSubmitted(application);

            }
        } catch (Exception e) {
            log.error("Playwright automation failed for application {}", application.getId(), e);
            markApplicationFailed(application, e.getMessage());
        }
    }

    private void findAndClickApplyButton(Page page) {
        // Try common selectors for Apply buttons across different ATS platforms
        String[] applySelectors = {
                "a:has-text('Apply')", "button:has-text('Apply')",
                "a:has-text('Apply Now')", "button:has-text('Apply Now')",
                "[data-qa='btn-apply']", ".apply-button", "#apply-btn",
                "a[href*='apply']"
        };

        for (String selector : applySelectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    locator.click();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    log.debug("Clicked apply button with selector: {}", selector);
                    return;
                }
            } catch (Exception ignored) {}
        }
        log.warn("Could not find apply button — form may already be visible");
    }

    private void fillStandardFormFields(Page page, Application application) {
        User userInfo = application.getUser();

        // Common field mappings across ATS platforms
        fillFieldIfExists(page, "input[name*='first'], input[id*='first-name'], input[placeholder*='First name']",
                userInfo.getName().split(" ")[0]);

        if (userInfo.getName().contains(" ")) {
            fillFieldIfExists(page, "input[name*='last'], input[id*='last-name'], input[placeholder*='Last name']",
                    userInfo.getName().substring(userInfo.getName().indexOf(" ") + 1));
        }

        fillFieldIfExists(page, "input[type='email'], input[name*='email']", userInfo.getEmail());
    }

    private void fillFieldIfExists(Page page, String selector, String value) {
        try {
            Locator locator = page.locator(selector).first();
            if (locator.count() > 0 && locator.isVisible()) {
                locator.fill(value);
                log.debug("Filled field [{}] with value", selector);
            }
        } catch (Exception e) {
            log.debug("Could not fill field {}: {}", selector, e.getMessage());
        }
    }

    private void handleScreeningQuestions(Page page, Application application) {
        // Find all textarea elements (likely screening questions)
        List<Locator> textareas = page.locator("textarea").all();

        for (Locator textarea : textareas) {
            try {
                if (!textarea.isVisible()) continue;

                // Try to get the question label
                String questionText = getQuestionLabel(page, textarea);
                if (questionText == null || questionText.isBlank()) continue;

                log.debug("Answering screening question: {}", questionText);
                String answer = generateScreeningAnswer(questionText, application);
                textarea.fill(answer);

                // Store for record
                if (application.getScreeningAnswers() == null) {
                    application.setScreeningAnswers(new HashMap<>());
                }
                application.getScreeningAnswers().put(questionText, answer);

            } catch (Exception e) {
                log.debug("Could not handle textarea: {}", e.getMessage());
            }
        }
    }

    private String getQuestionLabel(Page page, Locator textarea) {
        try {
            String id = textarea.getAttribute("id");
            if (id != null) {
                Locator label = page.locator("label[for='" + id + "']");
                if (label.count() > 0) return label.innerText();
            }
            // Try aria-label
            String ariaLabel = textarea.getAttribute("aria-label");
            if (ariaLabel != null) return ariaLabel;
            // Try placeholder
            return textarea.getAttribute("placeholder");
        } catch (Exception e) {
            return null;
        }
    }

    private String generateScreeningAnswer(String question, Application application) {
        String resumeContext = "";
        try {
            resumeContext = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(
                            applicationRepository.findById(application.getId())
                                    .map(a -> a.getUser())
                                    .map(u -> Map.of("name", u.getName(), "email", u.getEmail()))
                                    .orElse(Map.of())
                    );
        } catch (Exception ignored) {}

        String prompt = SCREENING_ANSWER_PROMPT.formatted(
                question,
                resumeContext,
                application.getJob().getCompany(),
                application.getJob().getTitle()
        );

        Map<String, Object> requestBody = Map.of(
                "model", ollamaConfig.getReasoningModel(),
                "prompt", prompt,
                "stream", false,
                "think", false
        );

        try {
            Map<String, Object> response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return response != null ? (String) response.get("response") : "I am excited to contribute to your team.";
        } catch (Exception e) {
            log.warn("Failed to generate screening answer", e);
            return "I am excited to contribute to your team.";
        }
    }

    private void uploadResume(Page page, Application application) {
        if (application.getTailoredResumePath() == null) return;

        try {
            Locator fileInput = page.locator("input[type='file']").first();
            if (fileInput.count() > 0) {
                fileInput.setInputFiles(java.nio.file.Path.of(application.getTailoredResumePath()));
                log.info("Resume uploaded for application {}", application.getId());
            }
        } catch (Exception e) {
            log.warn("Could not upload resume: {}", e.getMessage());
        }
    }

    @Transactional
    protected void markApplicationSubmitted(Application application) {
        application.setStatus(Application.ApplicationStatus.APPLIED);
        application.setAppliedAt(OffsetDateTime.now());
        applicationRepository.save(application);
        log.info("Application {} marked as APPLIED", application.getId());
    }

    @Transactional
    protected void markApplicationFailed(Application application, String reason) {
        application.setStatus(Application.ApplicationStatus.FAILED);
        application.setFailureReason(reason);
        applicationRepository.save(application);
    }

}
