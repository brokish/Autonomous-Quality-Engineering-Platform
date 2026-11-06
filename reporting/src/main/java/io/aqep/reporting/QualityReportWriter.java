package io.aqep.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.ExecutableScenario;
import io.aqep.shared.ExecutionResult;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.function.Predicate;

public final class QualityReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public void write(AnalysisResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path allureDirectory = outputDirectory.resolve("allure-results");
        Files.createDirectories(allureDirectory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputDirectory.resolve("analysis.json").toFile(), result);
        Files.writeString(outputDirectory.resolve("summary.html"), html(result));
        writeAllureResult(result, allureDirectory);
    }

    /** Appends the AI-generated scenarios and their execution results to an existing summary.html. */
    public void appendExecutedScenarios(Path outputDirectory, List<ExecutableScenario> scenarios,
                                        List<ExecutionResult> results) throws IOException {
        StringBuilder html = new StringBuilder("<h2>AI-generated scenarios executed</h2>");
        if (scenarios.isEmpty()) {
            html.append("<p>No executable scenarios were generated.</p>");
        }
        for (ExecutableScenario scenario : scenarios) {
            ExecutionResult outcome = results.stream()
                    .filter(r -> r.scenarioName().equals(scenario.name())).findFirst().orElse(null);
            html.append("<h3>").append(outcome == null ? "NOT RUN" : outcome.success() ? "✅ PASS" : "❌ FAIL")
                    .append(" — ").append(escape(scenario.name())).append("</h3>");
            scenario.description().ifPresent(d -> html.append("<p>").append(escape(d)).append("</p>"));
            html.append("<table><thead><tr><th>Step</th><th>Details</th><th>Result</th></tr></thead><tbody>");
            int index = 0;
            for (var action : scenario.actions()) {
                var actionOutcome = outcome != null && index < outcome.actionOutcomes().size()
                        ? outcome.actionOutcomes().get(index) : null;
                html.append("<tr><td>Action</td><td>").append(escape(String.valueOf(action))).append("</td><td>")
                        .append(actionOutcome == null ? "not run" : actionOutcome.success() ? "passed"
                                : "failed: " + escape(actionOutcome.errorMessage().orElse("")))
                        .append("</td></tr>");
                index++;
            }
            for (var assertion : scenario.assertions()) {
                html.append("<tr><td>Assertion</td><td>").append(escape(String.valueOf(assertion)))
                        .append("</td><td></td></tr>");
            }
            html.append("</tbody></table>");
            if (outcome != null) {
                outcome.errorMessage().ifPresent(m -> html.append("<p>Error: ").append(escape(m)).append("</p>"));
                for (String shot : outcome.screenshotPaths()) {
                    html.append("<p>Screenshot: <code>").append(escape(shot)).append("</code></p>");
                }
            }
        }
        Path summary = outputDirectory.resolve("summary.html");
        String current = Files.readString(summary);
        int end = current.lastIndexOf("</body>");
        Files.writeString(summary, end < 0 ? current + html
                : current.substring(0, end) + html + current.substring(end));
    }

    private void writeAllureResult(AnalysisResult result, Path directory) throws IOException {
        long start = result.analyzedAt().toEpochMilli();
        long stop = Instant.now().toEpochMilli();
        String uuid = UUID.randomUUID().toString();
        ObjectNode testResult = mapper.createObjectNode()
                .put("uuid", uuid)
                .put("name", "Autonomous website quality analysis")
                .put("fullName", "io.aqep.analysis." + safeName(result.targetUrl()))
                .put("status", result.qualityScore() < 70 ? "failed" : "passed")
                .put("stage", "finished")
                .put("start", start)
                .put("stop", stop);
        testResult.putArray("steps");
        ArrayNode attachments = testResult.putArray("attachments");
        for (PageResult page : result.pages()) {
            if (page.screenshotPath().isBlank()) continue;
            Path screenshot = Path.of(page.screenshotPath());
            if (!Files.isRegularFile(screenshot)) continue;
            String attachmentName = "screenshot-" + UUID.randomUUID() + ".png";
            Files.copy(screenshot, directory.resolve(attachmentName));
            attachments.addObject().put("name", "Screenshot: " + page.url())
                    .put("source", attachmentName).put("type", "image/png");
        }
        for (PageResult page : result.pages()) {
            for (var check : page.formChecks()) {
                if (check.screenshotPath().isBlank()) continue;
                Path screenshot = Path.of(check.screenshotPath());
                if (!Files.isRegularFile(screenshot)) continue;
                String attachmentName = "form-prefill-" + UUID.randomUUID() + ".png";
                Files.copy(screenshot, directory.resolve(attachmentName));
                attachments.addObject().put("name", "Pre-submit form " + check.formNumber() + ": " + page.url())
                        .put("source", attachmentName).put("type", "image/png");
            }
        }        testResult.putArray("parameters");
        testResult.putArray("links");
        ArrayNode labels = testResult.putArray("labels");
        labels.addObject().put("name", "suite").put("value", "Autonomous Quality Engineering");
        labels.addObject().put("name", "host").put("value", "local-browser");
        labels.addObject().put("name", "tag").put("value", "quality-score:" + result.qualityScore());
        ObjectNode statusDetails = testResult.putObject("statusDetails");
        if (result.qualityScore() < 70) {
            statusDetails.put("message", "Quality score below 70: " + result.qualityScore());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(uuid + "-result.json").toFile(), testResult);
    }

    private String html(AnalysisResult result) {
        StringBuilder pageRows = new StringBuilder();
        for (PageResult page : result.pages()) {
            pageRows.append("<tr><td>").append(escape(page.url())).append("</td><td>")
                    .append(escape(page.title())).append("</td><td>").append(page.findings().size())
                    .append("</td><td>");
            if (!page.screenshotPath().isBlank()) {
                String name = Path.of(page.screenshotPath()).getFileName().toString();
                pageRows.append("<a href=\"screenshots/").append(escape(name)).append("\">Screenshot</a>");
            }
            pageRows.append("</td></tr>");
        }
        StringBuilder formRows = new StringBuilder();
        for (PageResult page : result.pages()) {
            for (var check : page.formChecks()) {
                formRows.append("<tr><td>").append(escape(page.url())).append("</td><td>").append(check.formNumber())
                        .append("</td><td>").append(check.valid() ? "Passed" : "Needs attention")
                        .append("</td><td>").append(check.fieldsFilled()).append("</td><td>")
                        .append(check.submitted() ? "Yes" : "No — stopped before submit").append("</td><td>")
                        .append(escape(String.join("; ", check.invalidControls()))).append("</td><td>")
                        .append(escape(check.screenshotPath())).append("</td></tr>");
            }
        }
        if (formRows.isEmpty()) formRows.append("<tr><td colspan=\"7\">No forms discovered</td></tr>");
        StringBuilder findingRows = new StringBuilder();
        for (Finding finding : result.findings()) {
            findingRows.append("<tr><td>").append(escape(finding.severity().name())).append("</td><td>")
                    .append(escape(finding.category())).append("</td><td>").append(escape(finding.pageUrl()))
                    .append("</td><td>").append(escape(finding.message())).append("</td><td>")
                    .append(escape(finding.evidence())).append("</td></tr>");
        }
        StringBuilder ai = new StringBuilder();
        for (String observation : result.aiObservations()) {
            ai.append("<li>").append(escape(observation)).append("</li>");
        }
        String executiveSummary = result.qualityScore() >= 90
                ? "The website appears healthy based on the deterministic checks performed."
                : result.qualityScore() >= 70
                    ? "The website is generally functional, with quality findings that should be reviewed."
                    : "The scan found significant quality risks that need review.";
        if (!result.findings().isEmpty()) {
            long highRisk = result.findings().stream().filter(finding -> finding.severity() == Finding.Severity.CRITICAL
                    || finding.severity() == Finding.Severity.HIGH).count();
            executiveSummary += " " + result.findings().size() + " finding(s) were recorded, including "
                    + highRisk + " critical or high severity finding(s).";
        }
        StringBuilder scenarios = new StringBuilder();
        for (var scenario : result.proposedScenarios()) {
            scenarios.append("<li><strong>").append(escape(scenario.title())).append("</strong> — ")
                    .append(escape(scenario.priority().name())).append(" / ")
                    .append(escape(scenario.category().name())).append(": ")
                    .append(escape(scenario.rationale())).append("</li>");
        }
        String scores = "<div class=\"metrics\"><p>Accessibility: <strong>" + score(result, finding ->
                finding.category().startsWith("accessibility-") || "keyboard-navigation".equals(finding.category())) +
                "/100</strong></p><p>Navigation: <strong>" + score(result, finding ->
                List.of("navigation-failure", "http-error").contains(finding.category())) +
                "/100</strong></p><p>UI stability: <strong>" + score(result, finding ->
                List.of("javascript-error", "network-failure", "http-error").contains(finding.category())) +
                "/100</strong></p><p>Visual issues: <strong>" + score(result, finding ->
                finding.category().startsWith("visual-") || "broken-image".equals(finding.category())) +
                "/100</strong></p></div>";
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Website quality report</title>
                <style>
                body{font:15px system-ui,sans-serif;max-width:1200px;margin:2rem auto;padding:0 1rem;color:#17212b}
                .score{font-size:2rem;font-weight:700;color:%s}.metrics{display:flex;gap:1rem;flex-wrap:wrap}.metrics p{background:#f2f5f8;padding:1rem;border-radius:.5rem}table{width:100%%;border-collapse:collapse;margin:1rem 0 2rem}
                th,td{text-align:left;padding:.65rem;border-bottom:1px solid #d9e0e7;vertical-align:top}
                th{background:#f2f5f8}td{overflow-wrap:anywhere}code{overflow-wrap:anywhere}
                </style></head><body><h1>Autonomous Quality Engineering Platform</h1>
                <p>Target: <code>%s</code></p><p>Analyzed: %s</p><h2>Executive summary</h2><p>%s</p>
                <p class="score">Quality score: %d / 100</p>%s<p>%d pages analyzed · %d findings</p>
                <h2>Pages</h2><table><thead><tr><th>URL</th><th>Title</th><th>Findings</th><th>Evidence</th></tr></thead>
                <tbody>%s</tbody></table>
                <h2>Pre-submit form validation</h2><p>Form fields were filled with synthetic values and validity was checked. AQEP did not submit any form.</p>
                <table><thead><tr><th>Page</th><th>Form</th><th>Status</th><th>Fields filled</th><th>Submitted</th><th>Invalid controls</th><th>Evidence screenshot</th></tr></thead><tbody>%s</tbody></table>
                <h2>Findings</h2><table><thead><tr><th>Severity</th><th>Category</th><th>Page</th><th>Issue</th><th>Evidence</th></tr></thead>
                <tbody>%s</tbody></table>
                <h2>AI proposed test strategy</h2><p>Proposed by AI; executed scenarios and their results are listed in the section below when AI execution is enabled.</p><ul>%s</ul>
                <h2>AI observations</h2><ul>%s</ul></body></html>
                """.formatted(result.qualityScore() >= 80 ? "#16794b" : "#b54708",
                escape(result.targetUrl()), escape(result.analyzedAt().toString()), escape(executiveSummary), result.qualityScore(),
                scores, result.pagesAnalyzed(), result.findings().size(), pageRows, formRows, findingRows, scenarios, ai);
    }

    private int score(AnalysisResult result, Predicate<Finding> category) {
        int deductions = result.findings().stream().filter(category).mapToInt(finding -> switch (finding.severity()) {
            case CRITICAL -> 20;
            case HIGH -> 12;
            case MEDIUM -> 5;
            case LOW -> 2;
            case INFO -> 0;
        }).sum();
        return Math.max(0, 100 - deductions);
    }
    private String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
