package io.aqep.cli.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aqep.reporting.QualityReportWriter;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.Comparator;
import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

class QualityReportWriterTest {
    @Test
    void writesEscapedHtmlJsonAndAllureResultsWithoutSerializingDom() throws Exception {
        Path tempDirectory = Files.createTempDirectory("aqep-report-test");
        try {
            Finding finding = new Finding("console", Finding.Severity.HIGH, "https://example.com",
                    "Script error", "<script>alert(1)</script>");
            PageResult page = new PageResult("https://example.com", "Example", "", List.of("Home"), "",
                    List.of("error"), List.of(), List.of(), List.of(finding), List.of(), "private-dom-marker");
            AnalysisResult result = new AnalysisResult("https://example.com", Instant.now(), 1, 88,
                    List.of(page), List.of(finding), List.of("local model observation"));

            new QualityReportWriter().write(result, tempDirectory);

            String html = Files.readString(tempDirectory.resolve("summary.html"));
            String json = Files.readString(tempDirectory.resolve("analysis.json"));
            assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
            assertFalse(html.contains("<script>alert(1)</script>"));
            assertFalse(json.contains("private-dom-marker"));
            Path allureResult;
            try (Stream<Path> files = Files.list(tempDirectory.resolve("allure-results"))) {
                allureResult = files.filter(path -> path.getFileName().toString().endsWith("-result.json"))
                        .findFirst().orElseThrow();
            }
            assertTrue(new ObjectMapper().readTree(Files.readString(allureResult)).has("uuid"));
        } finally {
            try (Stream<Path> files = Files.walk(tempDirectory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
