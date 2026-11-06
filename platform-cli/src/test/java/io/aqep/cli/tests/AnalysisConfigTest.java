package io.aqep.cli.tests;

import io.aqep.shared.AnalysisConfig;
import java.nio.file.Path;
import java.time.Duration;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

class AnalysisConfigTest {
    @Test
    void appliesLocalAiDefaultsAndValidatesCrawlLimit() {
        AnalysisConfig defaults = AnalysisConfig.defaults("https://example.com");
        assertEquals(defaults.textModel(), "qwen2.5-coder:7b");
        assertEquals(defaults.visionModel(), "llava");
        expectThrows(IllegalArgumentException.class, () -> new AnalysisConfig("https://example.com",
                Path.of("out"), 101, Duration.ofSeconds(1), null, null, null));
        expectThrows(IllegalArgumentException.class, () -> new AnalysisConfig("https://example.com",
                Path.of("out"), 1, Duration.ofSeconds(1), "https://api.example.com", null, null));
        expectThrows(IllegalArgumentException.class, () -> new AnalysisConfig("https://example.com",
                Path.of("out"), 1, Duration.ofSeconds(1), null, "gpt-4o", null));
    }
}
