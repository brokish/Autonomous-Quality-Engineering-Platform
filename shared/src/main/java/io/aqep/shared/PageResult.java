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
        List<DiscoveredElement> discoveredElements,
        List<FormCheck> formChecks,
        List<Finding> findings,
        List<String> stackTraces,
        @JsonIgnore String domSnapshot) {
    public PageResult {
        headings = List.copyOf(headings);
        consoleErrors = List.copyOf(consoleErrors);
        networkFailures = List.copyOf(networkFailures);
        discoveredLinks = List.copyOf(discoveredLinks);
        discoveredElements = List.copyOf(discoveredElements);
        formChecks = List.copyOf(formChecks);
        findings = List.copyOf(findings);
        stackTraces = List.copyOf(stackTraces);
    }

    public PageResult(String url, String title, String description, List<String> headings, String screenshotPath,
                      List<String> consoleErrors, List<String> networkFailures, List<String> discoveredLinks,
                      List<DiscoveredElement> discoveredElements, List<Finding> findings,
                      List<String> stackTraces, String domSnapshot) {
        this(url, title, description, headings, screenshotPath, consoleErrors, networkFailures, discoveredLinks,
                discoveredElements, List.of(), findings, stackTraces, domSnapshot);
    }

    public PageResult(String url, String title, String description, List<String> headings, String screenshotPath,
                      List<String> consoleErrors, List<String> networkFailures, List<String> discoveredLinks,
                      List<Finding> findings, List<String> stackTraces, String domSnapshot) {
        this(url, title, description, headings, screenshotPath, consoleErrors, networkFailures, discoveredLinks,
                List.of(), List.of(), findings, stackTraces, domSnapshot);
    }
}
