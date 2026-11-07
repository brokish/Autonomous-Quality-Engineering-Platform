package io.aqep.core.execution;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.aqep.core.UrlSafetyPolicy;
import io.aqep.healing.LocatorMemory;
import io.aqep.shared.Action;
import io.aqep.shared.ExecutableScenario;
import io.aqep.shared.ExecutionResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Runs executable scenarios in a headless browser, each in an isolated browser context. */
public final class ScenarioBatchRunner {
    private final UrlSafetyPolicy safetyPolicy = new UrlSafetyPolicy();
    private final LocatorHealer healer;
    private final LocatorMemory memory;
    private final List<String> healedLocators = new ArrayList<>();

    public ScenarioBatchRunner() {
        this(null, null);
    }

    /** Both arguments are optional; with them, broken locators are repaired and remembered across runs. */
    public ScenarioBatchRunner(LocatorHealer healer, LocatorMemory memory) {
        this.healer = healer;
        this.memory = memory;
    }

    public List<String> healedLocators() {
        return List.copyOf(healedLocators);
    }

    public List<ExecutionResult> run(List<ExecutableScenario> scenarios, Path outputDirectory) {
        return run(scenarios, outputDirectory, 1);
    }

    /** firstIndex numbers the screenshot folders (scenario-NN) so separate calls do not overwrite each other. */
    public List<ExecutionResult> run(List<ExecutableScenario> scenarios, Path outputDirectory, int firstIndex) {
        List<ExecutionResult> results = new ArrayList<>();
        if (scenarios.isEmpty()) return results;
        ScenarioExecutor executor = new ScenarioExecutor(new ActionExecutor());
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            int index = firstIndex;
            for (ExecutableScenario scenario : scenarios) {
                validateUrls(scenario);
                try (BrowserContext context = browser.newContext()) {
                    Page page = context.newPage();
                    Path screenshots = outputDirectory.resolve("scenarios")
                            .resolve(String.format("scenario-%02d", index++));
                    ActionContext actionContext = new ActionContext(page, context, screenshots, Optional.empty())
                            .withHealing(healer, memory);
                    results.add(executor.execute(scenario, actionContext));
                    healedLocators.addAll(actionContext.getHealedLocators());
                }
            }
        }
        return results;
    }

    private void validateUrls(ExecutableScenario scenario) {
        safetyPolicy.validatePublicUrl(scenario.targetPage());
        for (Action action : scenario.actions()) {
            if (action instanceof Action.NavigateAction navigate) safetyPolicy.validatePublicUrl(navigate.url());
        }
    }
}
