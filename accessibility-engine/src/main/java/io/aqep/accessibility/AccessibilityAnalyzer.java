package io.aqep.accessibility;

import com.microsoft.playwright.Page;
import io.aqep.shared.Finding;
import java.util.ArrayList;
import java.util.List;

public final class AccessibilityAnalyzer {
    public List<Finding> analyze(Page page, String url) {
        List<Finding> findings = new ArrayList<>();
        Object unlabeledResult = page.locator("input:not([type=hidden]), select, textarea").evaluateAll(
                "elements => elements.filter(el => !el.disabled && !el.getAttribute('aria-label') && " +
                        "!el.getAttribute('aria-labelledby') && !el.labels?.length)" +
                        ".map(el => `${el.tagName.toLowerCase()}${el.type ? '[type=' + el.type + ']' : ''}`)");
        List<?> unlabeledControls = unlabeledResult instanceof List<?> items ? items : List.of();
        for (Object control : unlabeledControls) {
            findings.add(new Finding("accessibility-label", Finding.Severity.HIGH, url,
                    "Form control has no programmatic label", String.valueOf(control)));
        }

        Object unnamedResult = page.locator("button, [role=button]").evaluateAll(
                "elements => elements.filter(el => !el.getAttribute('aria-label') && " +
                        "!el.getAttribute('aria-labelledby') && !el.innerText.trim() && " +
                        "!el.querySelector('img[alt]:not([alt=\"\"])'))" +
                        ".map(el => el.outerHTML.slice(0, 180))");
        List<?> unnamedButtons = unnamedResult instanceof List<?> items ? items : List.of();
        for (Object button : unnamedButtons) {
            findings.add(new Finding("accessibility-name", Finding.Severity.HIGH, url,
                    "Button has no accessible name", String.valueOf(button)));
        }

        Object imagesResult = page.locator("img:not([alt])").evaluateAll(
                "images => images.map(image => image.currentSrc || image.src)");
        List<?> imagesWithoutAlt = imagesResult instanceof List<?> items ? items : List.of();
        for (Object image : imagesWithoutAlt) {
            findings.add(new Finding("accessibility-alt-text", Finding.Severity.MEDIUM, url,
                    "Image is missing its alt attribute", String.valueOf(image)));
        }

        Object untabbableResult = page.locator("a[href], button, input:not([type=hidden]), select, textarea, [role=button]")
                .evaluateAll("elements => elements.filter(el => !el.disabled && el.tabIndex < 0)" +
                        ".map(el => el.outerHTML.slice(0, 180))");
        List<?> untabbableControls = untabbableResult instanceof List<?> items ? items : List.of();
        for (Object element : untabbableControls) {
            findings.add(new Finding("keyboard-navigation", Finding.Severity.MEDIUM, url,
                    "Interactive element is excluded from sequential keyboard navigation", String.valueOf(element)));
        }
        return findings;
    }
}
