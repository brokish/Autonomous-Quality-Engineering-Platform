package io.aqep.cli.tests;

import io.aqep.accessibility.AccessibilityAnalyzer;
import io.aqep.ai.OllamaQualityAgent;
import io.aqep.cli.steps.WebsiteAnalysisSteps;
import io.aqep.core.SiteCrawler;
import io.aqep.core.UrlSafetyPolicy;
import io.aqep.core.execution.ScenarioBatchRunner;
import io.aqep.generated.support.GeneratedTestWriter;
import io.aqep.healing.LocatorMemory;
import java.io.IOException;
import io.aqep.reporting.QualityReportWriter;
import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.ExecutableScenario;
import io.aqep.shared.ExecutionResult;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import io.aqep.ui.UiQualityAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class WebsiteAnalysisTest {
    private AnalysisConfig config;
    private AnalysisResult result;
    private boolean aiEnabled;
    private String generatedTestsInfo = "";
    private List<ExecutableScenario> plannedScenarios = List.of();
    private final List<ExecutionResult> executedResults = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger scenarioCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    private ScenarioBatchRunner scenarioRunner;
    private final OllamaQualityAgent aiAgent = new OllamaQualityAgent();

    @Test
    @Parameters({"targetUrl", "enableAi"})
    public void scansConfiguredWebsite(@Optional("https://example.com") String suiteTargetUrl,
                                       @Optional("false") String enableAi) throws Exception {
        aiEnabled = Boolean.parseBoolean(System.getProperty("aqep.ai", enableAi));
        String targetUrl = System.getProperty("aqep.url", suiteTargetUrl);
        config = AnalysisConfig.defaults(targetUrl);
        WebsiteAnalysisSteps steps = new WebsiteAnalysisSteps(
                new SiteCrawler(new UrlSafetyPolicy(), new UiQualityAnalyzer(), new AccessibilityAnalyzer()),
                aiAgent,
                new QualityReportWriter());
        result = steps.analyzeAndReport(config, aiEnabled);
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
         // Pages that failed to load (navigation-failure finding) cannot have a screenshot.
        long loadedPages = result.pages().stream()
                .filter(page -> page.findings().stream().noneMatch(f -> "navigation-failure".equals(f.category())))
                .count();
        Assert.assertEquals(screenshotCount, loadedPages,
                "Each successfully loaded page should have a saved screenshot");
        Reporter.log("Screenshots captured: " + screenshotCount, true);
        result.pages().stream().filter(page -> !page.screenshotPath().isBlank()).map(PageResult::screenshotPath)
                .map(Path::of).filter(Files::isRegularFile).forEach(WebsiteAnalysisTest::attachImage);
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

    /** Fails when the site has more HIGH findings or a lower score than the configured thresholds allow. */
    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void meetsQualityGates() {
        int maxHigh = Integer.getInteger("aqep.maxHigh", 0);
        int minScore = Integer.getInteger("aqep.minScore", 70);
        List<Finding> high = result.findings().stream()
                .filter(finding -> finding.severity() == Finding.Severity.HIGH).toList();
        Reporter.log("Quality score " + result.qualityScore() + " (min " + minScore + "), HIGH findings "
                + high.size() + " (max " + maxHigh + ")", true);
        high.stream().limit(10).forEach(finding -> Reporter.log("  HIGH " + finding.category() + " @ "
                + finding.pageUrl() + ": " + finding.message(), true));
        Assert.assertTrue(high.size() <= maxHigh,
                high.size() + " HIGH-severity finding(s) exceed the allowed " + maxHigh);
        Assert.assertTrue(result.qualityScore() >= minScore,
                "Quality score " + result.qualityScore() + " is below the required " + minScore);
    }

    @Test(dependsOnMethods = "scansConfiguredWebsite")
    public void aiAnalyzesRealSiteResults() {
        if (!aiEnabled) throw new SkipException("AI analysis disabled (enableAi=false)");
        result.aiObservations().forEach(observation -> Reporter.log("AI: " + observation, true));
        Assert.assertFalse(result.aiObservations().isEmpty(), "Ollama should produce observations");
        Assert.assertTrue(result.aiObservations().stream()
                        .noneMatch(o -> o.startsWith("Root-cause analysis unavailable")
                                || o.startsWith("AI test strategy unavailable")),
                "Root-cause analysis and test strategy must succeed: " + result.aiObservations());
        if (result.proposedScenarios().isEmpty()) {
            long blocked = result.findings().stream()
                    .filter(f -> "http-error".equals(f.category()) || "navigation-failure".equals(f.category())).count();
            throw new SkipException("No AI scenarios: the crawl found no testable elements"
                    + (blocked > 0 ? " (the site returned errors/blocked automated access, e.g. HTTP 403)" : "")
                    + ". Nothing to generate for this URL.");
        }
    }

    /** Shown in the IDE test tree as the scenario title. */
    public record NamedScenario(ExecutableScenario scenario) {
        @Override
        public String toString() {
            return scenario.name();
        }
    }

    @DataProvider(name = "aiScenarios")
    public Object[][] aiScenarios() throws Exception {
        if (!aiEnabled || result == null) return new Object[0][];
        plannedScenarios = aiAgent.planExecutableScenarios(result, config);
        Reporter.log("AI generated " + plannedScenarios.size() + " executable scenario(s)", true);
        return plannedScenarios.stream().map(scenario -> new Object[] {new NamedScenario(scenario)})
                .toArray(Object[][]::new);
    }

    /** One AI scenario per test, so each shows up (and passes or fails) separately in the IDE. */
    @Test(dependsOnMethods = "aiAnalyzesRealSiteResults", dataProvider = "aiScenarios")
    public void aiScenarioFlowWorks(NamedScenario named) {
        int index = scenarioCounter.incrementAndGet();
        ExecutionResult outcome = scenarioRunner().run(List.of(named.scenario()), config.outputDirectory(), index)
                .get(0);
        executedResults.add(outcome);
        recordAllureSteps(named.scenario(), outcome);
        attachScreenshots(config.outputDirectory().resolve("scenarios").resolve(String.format("scenario-%02d", index)));
        String line = describe(outcome);
        Reporter.log(line, true);
        Assert.assertTrue(outcome.success(), line);
    }

    /** Attaches every PNG in the folder (oldest first) to the current Allure test. */
    private static void attachScreenshots(Path directory) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            files.filter(file -> file.toString().endsWith(".png")).sorted().forEach(WebsiteAnalysisTest::attachImage);
        } catch (IOException ignored) {
            // Missing evidence must not fail the test.
        }
    }

    private static void attachImage(Path file) {
        try {
            io.qameta.allure.Allure.addAttachment(file.getFileName().toString().replaceAll("-\\d{10,}\\.png$", ""),
                    "image/png", Files.newInputStream(file), ".png");
        } catch (IOException | RuntimeException ignored) {
            // Missing evidence must not fail the test.
        }
    }

    /** Shows the executed flow in the Allure report as named steps (actions, then assertions) with screenshots. */
    private void recordAllureSteps(ExecutableScenario scenario, ExecutionResult outcome) {
        io.qameta.allure.Allure.description(scenario.description().orElse(scenario.name())
                + "\nStart page: " + scenario.targetPage());
        io.qameta.allure.Allure.step("Open " + scenario.targetPage(), io.qameta.allure.model.Status.PASSED);
        outcome.actionOutcomes().forEach(action -> io.qameta.allure.Allure.step(
                action.actionType() + ": " + action.action() + action.errorMessage().map(e -> " - " + e).orElse(""),
                action.success() ? io.qameta.allure.model.Status.PASSED : io.qameta.allure.model.Status.FAILED));
        outcome.assertionOutcomes().forEach(assertion -> io.qameta.allure.Allure.step(
                "Verify " + assertion.assertionType() + ": " + assertion.assertion()
                        + assertion.failureReason().map(e -> " - " + e).orElse(""),
                assertion.success() ? io.qameta.allure.model.Status.PASSED : io.qameta.allure.model.Status.FAILED));
    }

    /** Builds the Allure HTML report after every test has finished and prints how to open it. */
    @org.testng.annotations.AfterSuite(alwaysRun = true)
    public void buildAllureReport() {
        Path results = Path.of("allure-results").toAbsolutePath();
        Path report = Path.of("allure-report").toAbsolutePath();
        try {
            if (!Files.isDirectory(results)) return;
            Process process = new ProcessBuilder("cmd", "/c", "npx", "--yes", "allure-commandline", "generate",
                    results.toString(), "--clean", "-o", report.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
                System.out.println(System.lineSeparator() + "=== ALLURE: report generation failed; run: npx allure-commandline generate "
                        + results + " --clean -o " + report);
                return;
            }
            boolean opened = false;
            if (!Boolean.getBoolean("aqep.noAllureOpen")) {
                // file:// links break Allure (it needs HTTP), so serve the report on localhost and open the browser.
                new ProcessBuilder("cmd", "/c", "npx", "--yes", "allure-commandline", "open", report.toString(),
                        "-p", "5050").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                opened = true;
            }
            System.out.println(System.lineSeparator() + "=== ALLURE REPORT ===" + System.lineSeparator()
                    + (opened ? "Report is served at: http://localhost:5050 (opening in the browser)"
                    : "Open with: npx allure-commandline open \"" + report + "\" -p 5050")
                    + System.lineSeparator() + "Do NOT open index.html directly (file://) - it shows errors.");
        } catch (IOException exception) {
            System.out.println("=== ALLURE: npx not available: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized ScenarioBatchRunner scenarioRunner() {
        if (scenarioRunner == null) {
            scenarioRunner = new ScenarioBatchRunner((failedLocator, dom) -> {
                try {
                    return aiAgent.suggestLocator(config, "Element previously located by " + failedLocator, dom);
                } catch (IOException exception) {
                    return java.util.Optional.empty();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return java.util.Optional.empty();
                }
            }, currentUserMemory());
        }
        return scenarioRunner;
    }

    private static LocatorMemory currentUserMemory() {
        try {
            return LocatorMemory.forCurrentUser();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private String describe(ExecutionResult outcome) {
        StringBuilder line = new StringBuilder(outcome.success() ? "PASS " : "FAIL ").append(outcome.scenarioName());
        outcome.errorMessage().ifPresent(message -> line.append(" - ").append(message));
        if (outcome.failedAssertionCount() > 0) {
            line.append(" - ").append(outcome.failedAssertionCount()).append(" failed assertion(s)");
        }
        outcome.actionOutcomes().stream().filter(action -> !action.success()).forEach(action ->
                line.append(System.lineSeparator()).append("    ").append(action.action()).append(": ")
                        .append(action.errorMessage().orElse("")));
        return line.toString();
    }

    /** Writes the scenario summary and report section and saves the passing scenarios as permanent tests. */
    private void finishAiScenarios() throws IOException {
        if (plannedScenarios.isEmpty() || config == null) return;
        scenarioRunner().healedLocators().forEach(healed -> Reporter.log("Self-healed locator: " + healed, true));
        StringBuilder summary = new StringBuilder();
        executedResults.forEach(outcome -> summary.append(describe(outcome)).append(System.lineSeparator()));
        Files.writeString(config.outputDirectory().resolve("ai-scenarios.txt"), summary.toString());
        new QualityReportWriter().appendExecutedScenarios(config.outputDirectory(), plannedScenarios, executedResults);
        persistPassedScenarios(plannedScenarios, executedResults);
    }
    /** Keeps verified (passing) AI scenarios as permanent POM tests; nothing generated earlier is removed. */
    private void persistPassedScenarios(List<ExecutableScenario> scenarios, List<ExecutionResult> outcomes)
            throws IOException {
        List<ExecutableScenario> passed = scenarios.stream()
                .filter(scenario -> outcomes.stream().anyMatch(
                        outcome -> outcome.scenarioName().equals(scenario.name()) && outcome.success()))
                .toList();
        Path resources = Path.of(System.getProperty("aqep.generatedResources", "src/test/resources"));
        Path sources = Path.of(System.getProperty("aqep.generatedSources", "src/test/java"));
        var written = new GeneratedTestWriter().write(passed, sources, resources);
        if (written.isEmpty()) {
            generatedTestsInfo = "No passing scenarios to save as permanent tests.";
        } else {
            var info = written.get();
            generatedTestsInfo = info.added() + " new test(s) saved, " + info.total() + " total for '" + info.site()
                    + "'." + System.lineSeparator() + "Sources: " + info.packageDirectory().toAbsolutePath().normalize()
                    + System.lineSeparator() + "Run them alone: -Dtestng.suiteXmlFile=src/test/resources/suites/generated/"
                    + info.suiteFile().getFileName() + " (or run any test class/method from the IDE)";
        }
        Reporter.log(generatedTestsInfo, true);
    }

    @AfterClass(alwaysRun = true)
    public void printReportLink() throws IOException {
        if (config == null) return;
        finishAiScenarios();
        Path summary = config.outputDirectory().resolve("summary.html").toAbsolutePath().normalize();
        if (!Files.isRegularFile(summary)) return;
        String banner = System.lineSeparator() + "=== AQEP REPORT (scan results, AI scenarios generated and executed) ==="
                + System.lineSeparator() + summary.toUri() + System.lineSeparator()
                + (generatedTestsInfo.isEmpty() ? "" : generatedTestsInfo + System.lineSeparator());
        System.out.println(banner);
        Reporter.log(banner, false);
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
