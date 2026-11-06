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
        List<String> aiObservations) {
    public AnalysisResult {
        pages = List.copyOf(pages);
        findings = List.copyOf(findings);
        aiObservations = List.copyOf(aiObservations);
    }
}
