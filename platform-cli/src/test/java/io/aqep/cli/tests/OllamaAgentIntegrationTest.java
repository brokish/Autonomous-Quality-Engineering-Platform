package io.aqep.cli.tests;

import static org.assertj.core.api.Assertions.assertThat;

import io.aqep.ai.OllamaQualityAgent;
import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.DiscoveredElement;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import java.time.Instant;
import java.util.List;
import org.testng.annotations.Test;

/** Exercises real local model inference; run with the dedicated Ollama suite. */
public class OllamaAgentIntegrationTest {
    @Test
    public void runsLocalRootCauseAndTestStrategyInference() {
        String url = "https://example.com";
        Finding finding = new Finding("accessibility-label", Finding.Severity.HIGH, url,
                "Form control has no programmatic label", "input[type=search]");
        PageResult page = new PageResult(url, "Example documentation", "", List.of("Documentation"), "",
                List.of(), List.of(), List.of(url + "/docs"),
                List.of(new DiscoveredElement(DiscoveredElement.Kind.SEARCH, "Search documentation",
                                "input[type=search]", "", ""),
                        new DiscoveredElement(DiscoveredElement.Kind.LINK, "API documentation", "a[href]",
                                url + "/docs", "")),
                List.of(finding), List.of(), "<input type=search><a href='/docs'>API documentation</a>");
        AnalysisResult input = new AnalysisResult(url, Instant.now(), 1, 88,
                List.of(page), List.of(finding), List.of());

        AnalysisResult enriched = new OllamaQualityAgent().enrich(input, AnalysisConfig.defaults(url));

        assertThat(enriched.aiObservations())
                .anySatisfy(observation -> assertThat(observation)
                        .startsWith("Root-cause analysis:")
                        .doesNotContain("unavailable"));
        assertThat(enriched.proposedScenarios()).isNotEmpty();
        assertThat(enriched.proposedScenarios()).allSatisfy(scenario ->
                assertThat(scenario.title()).isNotBlank());
    }
}
