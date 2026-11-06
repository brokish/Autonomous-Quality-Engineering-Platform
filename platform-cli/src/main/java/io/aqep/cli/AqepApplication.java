package io.aqep.cli;

import io.aqep.accessibility.AccessibilityAnalyzer;
import io.aqep.ai.OllamaQualityAgent;
import io.aqep.cli.steps.WebsiteAnalysisSteps;
import io.aqep.core.SiteCrawler;
import io.aqep.core.UrlSafetyPolicy;
import io.aqep.reporting.QualityReportWriter;
import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;
import io.aqep.ui.UiQualityAnalyzer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class AqepApplication {
    private AqepApplication() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0])) {
            printUsage();
            return;
        }
        String targetUrl = args[0];
        Map<String, String> options = parseOptions(args);
        AnalysisConfig defaults = AnalysisConfig.defaults(targetUrl);
        AnalysisConfig config = new AnalysisConfig(
                targetUrl,
                Path.of(options.getOrDefault("--out", env("AQEP_OUTPUT_DIR", "artifacts"))),
                Integer.parseInt(options.getOrDefault("--max-pages", env("AQEP_MAX_PAGES", "10"))),
                Duration.ofSeconds(Long.parseLong(options.getOrDefault("--timeout-seconds",
                        env("AQEP_TIMEOUT_SECONDS", "25")))),
                options.getOrDefault("--ollama", env("OLLAMA_HOST", defaults.ollamaBaseUrl())),
                options.getOrDefault("--text-model", env("AQEP_TEXT_MODEL", defaults.textModel())),
                options.getOrDefault("--vision-model", env("AQEP_VISION_MODEL", defaults.visionModel())));

        WebsiteAnalysisSteps steps = new WebsiteAnalysisSteps(
                new SiteCrawler(new UrlSafetyPolicy(), new UiQualityAnalyzer(), new AccessibilityAnalyzer()),
                new OllamaQualityAgent(), new QualityReportWriter());
        AnalysisResult result = steps.analyzeAndReport(config, !options.containsKey("--no-ai"));
        System.out.printf("Analyzed %d page(s); quality score %d/100; %d finding(s).%n",
                result.pagesAnalyzed(), result.qualityScore(), result.findings().size());
        System.out.println("Reports: " + config.outputDirectory().toAbsolutePath());
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int index = 1; index < args.length; index++) {
            String option = args[index];
            if ("--no-ai".equals(option)) {
                options.put(option, "true");
            } else if (option.startsWith("--") && index + 1 < args.length && !args[index + 1].startsWith("--")) {
                options.put(option, args[++index]);
            } else {
                throw new IllegalArgumentException("Invalid or incomplete option: " + option);
            }
        }
        return options;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void printUsage() {
        System.out.println("""
                Autonomous Quality Engineering Platform
                Usage: aqep <public-http(s)-url> [--max-pages N] [--out DIR] [--timeout-seconds N]
                       [--ollama URL] [--text-model NAME] [--vision-model NAME] [--no-ai]
                Ollama defaults: qwen2.5-coder:7b for root cause; llava for visual analysis.
                Crawling is read-only, same-origin, and limited to 100 pages maximum.
                """);
    }
}
