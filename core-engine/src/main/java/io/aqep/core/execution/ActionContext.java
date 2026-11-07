package io.aqep.core.execution;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import io.aqep.healing.LocatorMemory;
import io.aqep.healing.LocatorRecipe;
import io.aqep.healing.SelfHealingLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Context for executing actions on a page. Holds the browser page, locator resolution,
 * screenshot capture, and log collection utilities.
 */
public class ActionContext {
    private static final long DEFAULT_NAVIGATION_TIMEOUT = 30_000;
    private static final long DEFAULT_ACTION_TIMEOUT = 10_000;

    private final Page page;
    private final BrowserContext browserContext;
    private final Path screenshotDir;
    private final Optional<SelfHealingLocator> selfHealing;
    private final List<String> capturedScreenshots;
    private final List<String> consoleLogs;
    private final List<String> networkLogs;
    private final LocatorResolutionCache locatorCache;
    private final List<String> healedLocators = new ArrayList<>();
    private LocatorHealer healer;
    private LocatorMemory memory;

    public ActionContext(
            Page page,
            BrowserContext browserContext,
            Path screenshotDir,
            Optional<SelfHealingLocator> selfHealing) {
        this.page = page;
        this.browserContext = browserContext;
        this.screenshotDir = screenshotDir;
        this.selfHealing = selfHealing;
        this.capturedScreenshots = new ArrayList<>();
        this.consoleLogs = new ArrayList<>();
        this.networkLogs = new ArrayList<>();
        this.locatorCache = new LocatorResolutionCache();

        setupPageListeners();
    }

    /**
     * Set up listeners for console and network events.
     */
    private void setupPageListeners() {
        page.onConsoleMessage(msg -> {
            String logEntry = String.format("[%s] %s", msg.type(), msg.text());
            consoleLogs.add(logEntry);
        });

        page.onResponse(response -> {
            if (response.status() >= 400) {
                String logEntry = String.format("[%d] %s", response.status(), response.url());
                networkLogs.add(logEntry);
            }
        });
    }

    /**
     * Resolve a locator string to a Playwright Locator object.
     * Uses cache and self-healing if configured.
     */
    public Locator resolveLocator(String locatorString, long timeoutMs) throws LocatorResolutionException {
        // Check cache first
        Optional<Locator> cached = locatorCache.get(locatorString);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            // Parse and create locator
            Locator locator = parseLocator(locatorString);
            locator.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            locatorCache.put(locatorString, locator);
            return locator;
        } catch (Exception e) {
            Optional<Locator> healed = heal(locatorString);
            if (healed.isPresent()) {
                locatorCache.put(locatorString, healed.get());
                return healed.get();
            }
            throw new LocatorResolutionException("Failed to resolve locator: " + locatorString, e);
        }
    }

    /**
     * Parse a locator string into a Playwright Locator.
     * Format: "TYPE:value" or just CSS selector
     * Examples:
     * - "role=button[name='Submit']"
     * - "text=Click me"
     * - "css=input.email"
     * - "#myId"
     */
    private Locator parseLocator(String locatorString) {
        if (locatorString.contains("=")) {
            // Format: TYPE:value
            String[] parts = locatorString.split("=", 2);
            String type = parts[0].trim().toLowerCase();
            String value = parts[1];

            return switch (type) {
                case "role" -> page.locator("role=" + value);
                case "label" -> page.locator("text=" + value);
                case "text" -> page.locator("text=" + value);
                case "css" -> page.locator(value);
                case "xpath" -> page.locator("xpath=" + value);
                case "data-testid" -> page.locator("[data-testid='" + value + "']");
                default -> page.locator(value);
            };
        } else {
            // Assume CSS selector
            return page.locator(locatorString);
        }
    }

    /**
     * Attempt to heal a broken locator using self-healing.
     */
    private Optional<Locator> heal(String failedLocator) {
        String key = page.url().replaceAll("[?#].*$", "") + " :: " + failedLocator;
        try {
            List<LocatorRecipe> candidates = new ArrayList<>();
            if (memory != null) memory.get(key).ifPresent(candidates::add);
            if (healer != null) healer.suggest(failedLocator, captureDom()).ifPresent(candidates::add);
            for (LocatorRecipe recipe : candidates) {
                Locator candidate = toLocator(recipe);
                if (candidate.count() == 1 && candidate.isVisible()) {
                    if (memory != null && memory.get(key).filter(recipe::equals).isEmpty()) {
                        memory.remember(key, recipe);
                    }
                    healedLocators.add(failedLocator + " -> " + recipe.strategy() + ":" + recipe.value());
                    return Optional.of(candidate);
                }
            }
        } catch (Exception ignored) {
            // Healing is best effort; the caller reports the original failure.
        }
        return Optional.empty();
    }

    private Locator toLocator(LocatorRecipe recipe) {
        return switch (recipe.strategy()) {
            case ROLE -> {
                String[] parts = recipe.value().split("\\|", 2);
                yield page.getByRole(com.microsoft.playwright.options.AriaRole.valueOf(parts[0].toUpperCase()),
                        new Page.GetByRoleOptions().setName(parts[1]).setExact(true));
            }
            case LABEL -> page.getByLabel(recipe.value(), new Page.GetByLabelOptions().setExact(true));
            case PLACEHOLDER -> page.getByPlaceholder(recipe.value(),
                    new Page.GetByPlaceholderOptions().setExact(true));
            case TEXT -> page.getByText(recipe.value(), new Page.GetByTextOptions().setExact(true));
            case TEST_ID -> page.getByTestId(recipe.value());
        };
    }

    /** Enables self-healing: remembered recipes are tried first, then the optional AI healer. */
    public ActionContext withHealing(LocatorHealer healer, LocatorMemory memory) {
        this.healer = healer;
        this.memory = memory;
        return this;
    }

    /** Locators repaired during this context's lifetime, for reporting. */
    public List<String> getHealedLocators() {
        return List.copyOf(healedLocators);
    }

    /**
     * Navigate to a URL.
     */
    public void navigateTo(String url, long timeoutMs) throws NavigationException {
        page.setDefaultNavigationTimeout(timeoutMs);
        try {
            page.navigate(url);
        } catch (Exception e) {
            throw new NavigationException("Failed to navigate to " + url, e);
        }
    }

    /**
     * Take a screenshot and save it.
     */
    public String captureScreenshot(String name) throws IOException {
        String filename = name + "-" + System.currentTimeMillis() + ".png";
        Path filePath = screenshotDir.resolve(filename);
        Files.createDirectories(screenshotDir);

        byte[] data = page.screenshot();
        Files.write(filePath, data);

        capturedScreenshots.add(filePath.toString());
        return filePath.toString();
    }

    /**
     * Capture the DOM as a string.
     */
    public String captureDom() {
        try {
            return (String) page.evaluate("() => new XMLSerializer().serializeToString(document.documentElement)");
        } catch (Exception e) {
            return "<!-- Failed to capture DOM: " + e.getMessage() + " -->";
        }
    }

    /**
     * Get all captured console log entries.
     */
    public String getConsoleLogs() {
        return String.join("\n", consoleLogs);
    }

    /**
     * Get all captured network errors.
     */
    public String getNetworkLogs() {
        return String.join("\n", networkLogs);
    }

    /**
     * Get all captured screenshot paths.
     */
    public List<String> getCapturedScreenshots() {
        return List.copyOf(capturedScreenshots);
    }

    /**
     * Reset logs for the next action (optional).
     */
    public void resetLogs() {
        consoleLogs.clear();
        networkLogs.clear();
    }

    /**
     * Get the underlying Playwright Page object.
     */
    public Page getPage() {
        return page;
    }

    /**
     * Get the browser context.
     */
    public BrowserContext getBrowserContext() {
        return browserContext;
    }

    /**
     * Exception for locator resolution failures.
     */
    public static class LocatorResolutionException extends Exception {
        public LocatorResolutionException(String message) {
            super(message);
        }

        public LocatorResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception for navigation failures.
     */
    public static class NavigationException extends Exception {
        public NavigationException(String message) {
            super(message);
        }

        public NavigationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Simple cache for resolved locators.
     */
    private static class LocatorResolutionCache {
        private final java.util.Map<String, Locator> cache = new java.util.HashMap<>();

        Optional<Locator> get(String key) {
            return Optional.ofNullable(cache.get(key));
        }

        void put(String key, Locator locator) {
            cache.put(key, locator);
        }

        void clear() {
            cache.clear();
        }
    }
}
