package io.aqep.cli.tests;

import io.aqep.accessibility.AccessibilityAnalyzer;
import io.aqep.ai.OllamaQualityAgent;
import io.aqep.cli.steps.WebsiteAnalysisSteps;
import io.aqep.core.SiteCrawler;
import io.aqep.core.UrlSafetyPolicy;
import io.aqep.reporting.QualityReportWriter;
import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import io.aqep.ui.UiQualityAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class WebsiteAnalysisTest {
    private AnalysisConfig config;
    private AnalysisResult result;

    @Test
    @Parameters("targetUrl")
    public void scansConfiguredWebsite(@Optional("https://example.com") String suiteTargetUrl) throws Exception {
        String targetUrl = System.getProperty("aqep.url", suiteTargetUrl);
        config = AnalysisConfig.defaults(targetUrl);
        WebsiteAnalysisSteps steps = new WebsiteAnalysisSteps(
                new SiteCrawler(new UrlSafetyPolicy(), new UiQualityAnalyzer(), new AccessibilityAnalyzer()),
                new OllamaQualityAgent(),
                new QualityReportWriter());
        result = steps.analyzeAndReport(config, false);
        Assert.assertEquals(result.targetUrl(), targetUrl, "The analysis should use the configured target URL");
        Assert.assertTrue(result.pagesAnalyzed() > 0, "The website scan should analyze at least one page");
        Reporter.log("Crawled " + result.pagesAnalyzed() + " page(s) from " + targetUrl, true);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void capturesPageMetadata() {
        Assert.assertTrue(result.pages().stream().allMatch(page -> !page.url().isBlank()),
                "Every analyzed page should include its URL");
        Reporter.log("Page metadata captured for " + result.pages().size() + " page(s)", true);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void capturesScreenshots() {
        long screenshotCount = result.pages().stream()
                .filter(page -> !page.screenshotPath().isBlank())
                .map(PageResult::screenshotPath)
                .map(Path::of)
                .filter(Files::isRegularFile)
                .count();
        Assert.assertEquals(screenshotCount, result.pages().size(),
                "Each analyzed page should have a saved screenshot");
        Reporter.log("Screenshots captured: " + screenshotCount, true);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void checksAccessibilityFindings() {
        List<Finding> findings = findingsWithCategories(
                "accessibility-label", "accessibility-name", "accessibility-alt-text", "keyboard-navigation");
        assertFindingRecordsAreComplete(findings);
        Reporter.log("Accessibility and keyboard findings: " + findings.size(), true);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void checksUiQualityFindings() {
        List<Finding> findings = findingsWithCategories("broken-image");
        assertFindingRecordsAreComplete(findings);
        Reporter.log("UI quality findings (including broken images): " + findings.size(), true);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void collectsConsoleAndNetworkDiagnostics() {
        int consoleErrors = result.pages().stream().mapToInt(page -> page.consoleErrors().size()).sum();
        int networkFailures = result.pages().stream().mapToInt(page -> page.networkFailures().size()).sum();
        Reporter.log("Browser diagnostics: " + consoleErrors + " console error(s), "
                + networkFailures + " network failure(s)", true);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void generatesQualityReport() {
        Assert.assertTrue(Files.isRegularFile(config.outputDirectory().resolve("summary.html")),
                "The website scan should generate the HTML summary");
        Assert.assertTrue(Files.isRegularFile(config.outputDirectory().resolve("analysis.json")),
                "The website scan should generate the JSON report");
        Reporter.log("HTML and JSON reports generated in " + config.outputDirectory().toAbsolutePath(), true);
    }

    private List<Finding> findingsWithCategories(String... categories) {
        return result.findings().stream()
                .filter(finding -> List.of(categories).contains(finding.category()))
                .toList();
    }

    private void assertFindingRecordsAreComplete(List<Finding> findings) {
        Assert.assertTrue(findings.stream().allMatch(finding -> !finding.pageUrl().isBlank()
                        && !finding.message().isBlank()),
                "Each finding should identify its page and describe the issue");
    }
}
