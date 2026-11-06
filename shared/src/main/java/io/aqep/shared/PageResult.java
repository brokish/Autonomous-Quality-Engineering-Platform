package io.aqep.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record PageResult(
        String url,
        String title,
        String description,
        List<String> headings,
        String screenshotPath,
        List<String> consoleErrors,
        List<String> networkFailures,
        List<String> discoveredLinks,
        List<Finding> findings,
        List<String> stackTraces,
        @JsonIgnore String domSnapshot) {
    public PageResult {
        headings = List.copyOf(headings);
        consoleErrors = List.copyOf(consoleErrors);
        networkFailures = List.copyOf(networkFailures);
        discoveredLinks = List.copyOf(discoveredLinks);
        findings = List.copyOf(findings);
        stackTraces = List.copyOf(stackTraces);
    }
}
