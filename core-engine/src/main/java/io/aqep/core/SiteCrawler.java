package io.aqep.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import io.aqep.accessibility.AccessibilityAnalyzer;
import io.aqep.core.pages.AnalyzedWebsitePage;
import io.aqep.shared.AnalysisConfig;
import io.aqep.shared.AnalysisResult;
import io.aqep.shared.Finding;
import io.aqep.shared.PageResult;
import io.aqep.ui.UiQualityAnalyzer;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SiteCrawler {
    private final UrlSafetyPolicy safetyPolicy;
    private final UiQualityAnalyzer uiAnalyzer;
    private final AccessibilityAnalyzer accessibilityAnalyzer;

    public SiteCrawler(UrlSafetyPolicy safetyPolicy, UiQualityAnalyzer uiAnalyzer,
                       AccessibilityAnalyzer accessibilityAnalyzer) {
        this.safetyPolicy = safetyPolicy;
        this.uiAnalyzer = uiAnalyzer;
        this.accessibilityAnalyzer = accessibilityAnalyzer;
    }

    public AnalysisResult analyze(AnalysisConfig config) throws IOException {
        URI target = safetyPolicy.validatePublicUrl(config.targetUrl());
        URI origin = originOf(target);
        Path screenshotDirectory = config.outputDirectory().resolve("screenshots");
        Files.createDirectories(screenshotDirectory);
        List<PageResult> pages = new ArrayList<>();
        ArrayDeque<URI> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(target);

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            BrowserContext context = browser.newContext();
            context.setDefaultNavigationTimeout(config.navigationTimeout().toMillis());
            context.route("**/*", route -> {
                try {
                    URI requestUri = safetyPolicy.validatePublicUrl(route.request().url());
                    if (route.request().isNavigationRequest() && !safetyPolicy.sameOrigin(origin, requestUri)) {
                        route.abort();
                    } else {
                        route.resume();
                    }
                } catch (RuntimeException exception) {
                    route.abort();
                }
            });
            Page page = context.newPage();
            AnalyzedWebsitePage websitePage = new AnalyzedWebsitePage(page, origin, uiAnalyzer, accessibilityAnalyzer);
            while (!pending.isEmpty() && pages.size() < config.maxPages()) {
                URI next = pending.removeFirst();
                String url = canonical(next);
                if (!visited.add(url)) continue;
                websitePage.beginPage();
                try {
                    page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    Path screenshot = screenshotDirectory.resolve(String.format("page-%03d.png", pages.size() + 1));
                    PageResult result = websitePage.inspect(url, screenshot);
                    pages.add(result);
                    result.discoveredLinks().stream().map(this::safeUri)
                            .filter(link -> link != null && safetyPolicy.sameOrigin(origin, link))
                            .filter(link -> !visited.contains(canonical(link)))
                            .forEach(pending::addLast);
                } catch (PlaywrightException exception) {
                    websitePage.recordNavigationFailure(url, exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage());
                    pages.add(new PageResult(url, "", "", List.of(), "", List.of(),
                            List.of(exception.getMessage() == null ? "Navigation failed" : exception.getMessage()),
                            List.of(), List.of(new Finding("navigation-failure", Finding.Severity.HIGH, url,
                            "Page could not be analyzed", "Navigation or page inspection failed")),
                            List.of(websitePage.stackTrace(exception)), ""));
                }
            }
            context.close();
        }
        List<Finding> findings = pages.stream().flatMap(result -> result.findings().stream()).toList();
        return new AnalysisResult(target.toString(), Instant.now(), pages.size(), score(findings), pages, findings, List.of());
    }

    private URI safeUri(String value) {
        try {
            return URI.create(value).normalize();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String canonical(URI uri) {
        return new URIWithoutFragment(uri).value();
    }

    private URI originOf(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), "/", null, null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid target origin", exception);
        }
    }

    private int score(List<Finding> findings) {
        int deductions = findings.stream().mapToInt(finding -> switch (finding.severity()) {
            case CRITICAL -> 20;
            case HIGH -> 12;
            case MEDIUM -> 5;
            case LOW -> 2;
            case INFO -> 0;
        }).sum();
        return Math.max(0, 100 - deductions);
    }

    private record URIWithoutFragment(String value) {
        private URIWithoutFragment(URI uri) {
            this(uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        }
    }

}
