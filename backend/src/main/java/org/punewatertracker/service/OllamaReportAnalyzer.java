package org.punewatertracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.punewatertracker.model.ReportUrgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class OllamaReportAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(OllamaReportAnalyzer.class);

    private final AtomicBoolean enabled;

    @Value("${app.ollama-host:http://localhost:11434}")
    private String ollamaHost;

    @Value("${app.ollama-model:llama3.2}")
    private String model;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaReportAnalyzer(@Value("${app.ollama-enabled:false}") boolean initiallyEnabled) {
        this.enabled = new AtomicBoolean(initiallyEnabled);
    }

    public record Analysis(ReportUrgency urgency, int urgencyConfidence, String urgencyReasoning,
                           boolean isDuplicate, Double duplicateConfidence, String duplicateReasoning) {}

    public boolean isEnabled() {
        return enabled.get();
    }

    /** @return the new state, so the caller (FeatureToggleController) can log/report what it became. */
    public boolean setEnabled(boolean newState) {
        enabled.set(newState);
        return enabled.get();
    }

    public Analysis analyze(String description, String candidateDescription) {
        if (!enabled.get() || description == null || description.isBlank()) {
            return null;
        }

        try {
            String prompt = buildPrompt(description, candidateDescription);
            String responseText = callOllama(prompt);
            return parseResponse(responseText, candidateDescription != null);
        } catch (Exception ex) {
            log.warn("Ollama call failed: {}. Is Ollama running locally (`ollama serve`)? "
                    + "Leaving this report unclassified.", ex.getMessage());
            return null;
        }
    }

    private String buildPrompt(String description, String candidateDescription) {
        boolean hasCandidate = candidateDescription != null && !candidateDescription.isBlank();

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are triaging a citizen-submitted water-infrastructure complaint for a municipal water tracker.\n\n");
        prompt.append("Report description: \"").append(description).append("\"\n\n");
        prompt.append("Classify its urgency as one of LOW, MEDIUM, HIGH, CRITICAL, considering duration, ");
        prompt.append("safety/health risk (contamination, sewage, illness), scope (how many people affected), ");
        prompt.append("and infrastructure damage (bursts, flooding).\n\n");

        if (hasCandidate) {
            prompt.append("A separate report was submitted nearby around the same time, with this description: \"")
                    .append(candidateDescription).append("\"\n");
            prompt.append("Judge whether these two reports likely describe the SAME underlying incident.\n\n");
        }

        prompt.append("Respond with ONLY valid JSON, no other text, in exactly this shape:\n");
        prompt.append("{\"urgency\": \"LOW|MEDIUM|HIGH|CRITICAL\", \"urgencyConfidence\": <integer 0-100>, ");
        prompt.append("\"urgencyReasoning\": \"<one short sentence>\"");
        if (hasCandidate) {
            prompt.append(", \"isDuplicate\": <true|false>, \"duplicateConfidence\": <number 0.0-1.0>, ");
            prompt.append("\"duplicateReasoning\": \"<one short sentence>\"");
        }
        prompt.append("}");

        return prompt.toString();
    }

    private String callOllama(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "format", "json"
        );

        String rawResponse = restClient.post()
                .uri(ollamaHost + "/api/generate")
                .header("content-type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            return root.path("response").asText();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Ollama response envelope: " + rawResponse, ex);
        }
    }

    private Analysis parseResponse(String responseText, boolean expectDuplicateFields) throws Exception {
        String cleaned = responseText.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

        JsonNode json = objectMapper.readTree(cleaned);

        ReportUrgency urgency = ReportUrgency.valueOf(json.path("urgency").asText("MEDIUM").toUpperCase());
        int urgencyConfidence = json.path("urgencyConfidence").asInt(50);
        String urgencyReasoning = json.path("urgencyReasoning").asText("");

        boolean isDuplicate = expectDuplicateFields && json.path("isDuplicate").asBoolean(false);
        Double duplicateConfidence = (expectDuplicateFields && json.has("duplicateConfidence"))
                ? json.path("duplicateConfidence").asDouble() : null;
        String duplicateReasoning = expectDuplicateFields ? json.path("duplicateReasoning").asText("") : null;

        return new Analysis(urgency, urgencyConfidence, urgencyReasoning, isDuplicate, duplicateConfidence, duplicateReasoning);
    }
}
