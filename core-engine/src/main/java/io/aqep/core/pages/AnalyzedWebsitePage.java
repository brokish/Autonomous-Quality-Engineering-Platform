package io.aqep.core.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import io.aqep.accessibility.AccessibilityAnalyzer;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import io.aqep.ui.UiQualityAnalyzer;
import com.microsoft.playwright.options.ScreenshotAnimations;
import java.net.URI;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public final class AnalyzedWebsitePage {
    private final Page page;
    private final URI origin;
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> networkFailures = new ArrayList<>();
    private final List<Finding> runtimeFindings = new ArrayList<>();
    private final UiQualityAnalyzer uiAnalyzer;
    private final AccessibilityAnalyzer accessibilityAnalyzer;

    public AnalyzedWebsitePage(Page page, URI origin, UiQualityAnalyzer uiAnalyzer,
                               AccessibilityAnalyzer accessibilityAnalyzer) {
        this.page = page;
        this.origin = origin;
        this.uiAnalyzer = uiAnalyzer;
        this.accessibilityAnalyzer = accessibilityAnalyzer;
        page.onConsoleMessage(message -> {
            if ("error".equalsIgnoreCase(message.type())) consoleErrors.add(message.text());
        });
        page.onPageError(error -> {
            consoleErrors.add(error);
            runtimeFindings.add(new Finding("javascript-error", Finding.Severity.HIGH, page.url(),
                    "Uncaught JavaScript error", truncate(error, 500)));
        });
        page.onRequestFailed(this::recordFailedRequest);
        page.onResponse(this::recordFailedResponse);
    }

    public void beginPage() {
        consoleErrors.clear();
        networkFailures.clear();
        runtimeFindings.clear();
    }

    public PageResult inspect(String url, Path screenshot) {
        List<Finding> findings = new ArrayList<>(runtimeFindings);
        findings.addAll(uiAnalyzer.analyze(page, url));
        findings.addAll(accessibilityAnalyzer.analyze(page, url));

        List<String> headings = strings(page.locator("h1, h2, h3").evaluateAll(
                "elements => elements.map(el => el.innerText.trim()).filter(Boolean).slice(0, 100)"));
        List<String> links = strings(page.locator("a[href]").evaluateAll(
                "elements => [...new Set(elements.map(el => el.href).filter(Boolean))].slice(0, 500)"))
                .stream().filter(this::isSameOriginLink).toList();
        String title = page.title();
        String description = (String) page.evaluate(
                "() => document.querySelector('meta[name=description]')?.content || ''");
        String domSnapshot = page.content();
        if (domSnapshot.length() > 30_000) domSnapshot = domSnapshot.substring(0, 30_000);
        page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true).setAnimations(
                ScreenshotAnimations.DISABLED));
        return new PageResult(url, title, description, headings, screenshot.toString(),
                consoleErrors, networkFailures, links, findings, List.of(), domSnapshot);
    }

    public void recordNavigationFailure(String url, String message) {
        networkFailures.add(message);
        runtimeFindings.add(new Finding("navigation-failure", Finding.Severity.HIGH, url,
                "Page navigation failed", truncate(message, 500)));
    }

    public String stackTrace(Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }

    private void recordFailedRequest(Request request) {
        String message = request.method() + " " + request.url() + " failed: " + request.failure();
        networkFailures.add(message);
        runtimeFindings.add(new Finding("network-failure", Finding.Severity.MEDIUM, page.url(),
                "Network request failed", truncate(message, 500)));
    }

    private void recordFailedResponse(Response response) {
        if (response.status() < 400) return;
        String message = response.status() + " " + response.url();
        networkFailures.add(message);
        runtimeFindings.add(new Finding("http-error", Finding.Severity.MEDIUM, page.url(),
                "HTTP request returned an error status", truncate(message, 500)));
    }

    private boolean isSameOriginLink(String value) {
        try {
            URI link = URI.create(value);
            return origin.getScheme().equalsIgnoreCase(link.getScheme())
                    && origin.getHost().equalsIgnoreCase(link.getHost())
                    && port(origin) == port(link);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
