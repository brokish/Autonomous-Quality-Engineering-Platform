package io.aqep.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

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
        testResult.putArray("parameters");
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
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Website quality report</title>
                <style>
                body{font:15px system-ui,sans-serif;max-width:1200px;margin:2rem auto;padding:0 1rem;color:#17212b}
                .score{font-size:2rem;font-weight:700;color:%s}table{width:100%%;border-collapse:collapse;margin:1rem 0 2rem}
                th,td{text-align:left;padding:.65rem;border-bottom:1px solid #d9e0e7;vertical-align:top}
                th{background:#f2f5f8}td{overflow-wrap:anywhere}code{overflow-wrap:anywhere}
                </style></head><body><h1>Autonomous Quality Engineering Platform</h1>
                <p>Target: <code>%s</code></p><p>Analyzed: %s</p>
                <p class="score">Quality score: %d / 100</p><p>%d pages analyzed · %d findings</p>
                <h2>Pages</h2><table><thead><tr><th>URL</th><th>Title</th><th>Findings</th><th>Evidence</th></tr></thead>
                <tbody>%s</tbody></table>
                <h2>Findings</h2><table><thead><tr><th>Severity</th><th>Category</th><th>Page</th><th>Issue</th><th>Evidence</th></tr></thead>
                <tbody>%s</tbody></table>
                <h2>AI observations</h2><ul>%s</ul></body></html>
                """.formatted(result.qualityScore() >= 80 ? "#16794b" : "#b54708",
                escape(result.targetUrl()), escape(result.analyzedAt().toString()), result.qualityScore(),
                result.pagesAnalyzed(), result.findings().size(), pageRows, findingRows, ai);
    }

    private String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
