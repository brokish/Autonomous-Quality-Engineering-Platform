package io.aqep.ui;

import com.microsoft.playwright.Page;
import io.aqep.shared.Finding;
import java.util.ArrayList;
import java.util.List;

public final class UiQualityAnalyzer {
    public List<Finding> analyze(Page page, String url) {
        List<Finding> findings = new ArrayList<>();
        Object result = page.locator("img").evaluateAll(
                "images => images.filter(image => image.complete && image.naturalWidth === 0)" +
                        ".map(image => image.currentSrc || image.src)");
        List<?> brokenImages = result instanceof List<?> items ? items : List.of();
        for (Object image : brokenImages) {
            findings.add(new Finding("broken-image", Finding.Severity.MEDIUM, url,
                    "Image failed to load", String.valueOf(image)));
        }
        return findings;
    }
}
