package io.aqep.shared;

import java.time.Instant;
import java.util.List;

public record AnalysisResult(
        String targetUrl,
        Instant analyzedAt,
        int pagesAnalyzed,
        int qualityScore,
        List<PageResult> pages,
        List<Finding> findings,
        List<String> aiObservations,
        List<QualityScenario> proposedScenarios) {
    public AnalysisResult {
        pages = List.copyOf(pages);
        findings = List.copyOf(findings);
        aiObservations = List.copyOf(aiObservations);
        proposedScenarios = List.copyOf(proposedScenarios);
    }

    public AnalysisResult(String targetUrl, Instant analyzedAt, int pagesAnalyzed, int qualityScore,
                          List<PageResult> pages, List<Finding> findings, List<String> aiObservations) {
        this(targetUrl, analyzedAt, pagesAnalyzed, qualityScore, pages, findings, aiObservations, List.of());
    }
}
