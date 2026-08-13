package org.punewatertracker.controller;

import org.punewatertracker.audit.Audited;
import org.punewatertracker.service.OllamaReportAnalyzer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/features")
public class FeatureToggleController {
    private final OllamaReportAnalyzer ollamaReportAnalyzer;

    public FeatureToggleController(OllamaReportAnalyzer ollamaReportAnalyzer) {
        this.ollamaReportAnalyzer = ollamaReportAnalyzer;
    }

    @GetMapping("/ollama")
    public Map<String, Boolean> ollamaStatus() {
        return Map.of("enabled", ollamaReportAnalyzer.isEnabled());
    }

    @Audited("TOGGLE_OLLAMA_FEATURE")
    @PostMapping("/ollama/enable")
    public Map<String, Boolean> enableOllama() {
        return Map.of("enabled", ollamaReportAnalyzer.setEnabled(true));
    }

    @Audited("TOGGLE_OLLAMA_FEATURE")
    @PostMapping("/ollama/disable")
    public Map<String, Boolean> disableOllama() {
        return Map.of("enabled", ollamaReportAnalyzer.setEnabled(false));
    }
}
