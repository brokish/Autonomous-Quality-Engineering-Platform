package io.aqep.core.execution;

import io.aqep.shared.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Executes a complete QualityScenario by executing actions and assertions in sequence.
 * Captures screenshots, logs, and DOM state throughout the execution.
 */
public class ScenarioExecutor {
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long DEFAULT_ACTION_TIMEOUT = 10_000;

    private final ActionExecutor actionExecutor;

    public ScenarioExecutor(ActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    /**
     * Execute an executable scenario end-to-end.
     *
     * @param scenario The scenario to execute
     * @param context  The action context (page, screenshots, logs)
     * @return Execution result with all details
     */
    public ExecutionResult execute(ExecutableScenario scenario, ActionContext context) {
        Instant startTime = Instant.now();
        ExecutionResult.Builder resultBuilder = new ExecutionResult.Builder()
                .scenarioName(scenario.name());

        try {
            // Step 1: Navigate to target page if needed
            if (!scenario.targetPage().isEmpty()) {
                try {
                    context.navigateTo(scenario.targetPage(), DEFAULT_ACTION_TIMEOUT);
                    context.captureScreenshot("initial-page");
                } catch (ActionContext.NavigationException e) {
                    return resultBuilder
                            .success(false)
                            .error("Navigation failed to " + scenario.targetPage(), e.getMessage())
                            .totalTime(Duration.between(startTime, Instant.now()))
                            .build();
                }
            }

            // Step 2: Execute preconditions if any
            if (!scenario.preconditions().isEmpty()) {
                for (Action precondition : scenario.preconditions()) {
                    try {
                        ActionOutcome outcome = executeActionWithRetry(precondition, context);
                        resultBuilder.addActionOutcome(outcome);
                        if (!outcome.success()) {
                            return resultBuilder
                                    .success(false)
                                    .error("Precondition failed", precondition.toString())
                                    .totalTime(Duration.between(startTime, Instant.now()))
                                    .build();
                        }
                    } catch (Exception e) {
                        return resultBuilder
                                .success(false)
                                .error("Precondition execution error", e.getMessage())
                                .totalTime(Duration.between(startTime, Instant.now()))
                                .build();
                    }
                }
            }

            // Step 3: Execute main actions
            for (Action action : scenario.actions()) {
                try {
                    ActionOutcome outcome = executeActionWithRetry(action, context);
                    resultBuilder.addActionOutcome(outcome);

                    if (!outcome.success()) {
                        // Action failed - some scenarios may allow continuation, some may require stop
                        if (scenario.stopOnActionFailure()) {
                            return resultBuilder
                                    .success(false)
                                    .error("Action failed and stopOnActionFailure is true",
                                            outcome.errorMessage().orElse("Unknown error"))
                                    .totalTime(Duration.between(startTime, Instant.now()))
                                    .build();
                        }
                    }
                } catch (Exception e) {
                    if (scenario.stopOnActionFailure()) {
                        return resultBuilder
                                .success(false)
                                .error("Unexpected error during action execution", e.getMessage())
                                .totalTime(Duration.between(startTime, Instant.now()))
                                .build();
                    }
                }
            }

            // Step 4: Execute assertions
            boolean allAssertionsPassed = true;
            for (Assertion assertion : scenario.assertions()) {
                try {
                    AssertionOutcome outcome = executeAssertion(assertion, context);
                    resultBuilder.addAssertionOutcome(outcome);

                    if (!outcome.success()) {
                        allAssertionsPassed = false;
                    }
                } catch (Exception e) {
                    resultBuilder.addAssertionOutcome(
                            AssertionOutcome.failure(
                                    assertion.assertionType(),
                                    assertion.toString(),
                                    "Exception during assertion: " + e.getMessage(),
                                    List.of(),
                                    context.captureDom(),
                                    Duration.ZERO));
                    allAssertionsPassed = false;
                }
            }

            // Step 5: Finalize result
            resultBuilder
                    .success(allAssertionsPassed)
                    .finalDom(context.captureDom())
                    .consoleLogs(context.getConsoleLogs())
                    .networkLogs(context.getNetworkLogs());

            for (String screenshot : context.getCapturedScreenshots()) {
                resultBuilder.addScreenshot(screenshot);
            }

            resultBuilder.totalTime(Duration.between(startTime, Instant.now()));

            return resultBuilder.build();

        } catch (Throwable e) {
            return resultBuilder
                    .success(false)
                    .error("Unexpected error during scenario execution", e.getMessage())
                    .totalTime(Duration.between(startTime, Instant.now()))
                    .build();
        }
    }

    /**
     * Execute an action with automatic retry on failure.
     */
    private ActionOutcome executeActionWithRetry(Action action, ActionContext context) throws IOException {
        Instant startTime = Instant.now();

        for (int attempt = 0; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                // Execute action
                actionExecutor.execute(action, context);

                // Capture screenshot on success
                String screenshot = context.captureScreenshot(action.actionType().toLowerCase() + "-success");

                return ActionOutcome.success(
                        action.actionType(),
                        action.toString(),
                        List.of(screenshot),
                        context.captureDom(),
                        Duration.between(startTime, Instant.now()));

            } catch (Exception e) {
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    // All retries exhausted
                    try {
                        String screenshot = context.captureScreenshot(action.actionType().toLowerCase() + "-failed");
                        return ActionOutcome.failure(
                                action.actionType(),
                                action.toString(),
                                e.getMessage(),
                                e.getStackTrace() != null ? formatStackTrace(e.getStackTrace()) : "",
                                List.of(screenshot),
                                context.captureDom(),
                                Duration.between(startTime, Instant.now()));
                    } catch (IOException ioE) {
                        return ActionOutcome.failure(
                                action.actionType(),
                                action.toString(),
                                e.getMessage(),
                                e.getStackTrace() != null ? formatStackTrace(e.getStackTrace()) : "",
                                List.of(),
                                context.captureDom(),
                                Duration.between(startTime, Instant.now()));
                    }
                }
                // Retry silently, add small delay
                try {
                    Thread.sleep(500 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during action retry", ie);
                }
            }
        }

        throw new RuntimeException("Action execution exhausted all retries");
    }

    /**
     * Execute an assertion and capture the outcome.
     */
    private AssertionOutcome executeAssertion(Assertion assertion, ActionContext context) throws IOException {
        Instant startTime = Instant.now();

        try {
            boolean passed = actionExecutor.executeAssertion(assertion, context);

            String screenshot = context.captureScreenshot(assertion.assertionType().toLowerCase() + "-" +
                    (passed ? "pass" : "fail"));

            if (passed) {
                return AssertionOutcome.success(
                        assertion.assertionType(),
                        assertion.toString(),
                        List.of(screenshot),
                        context.captureDom(),
                        Duration.between(startTime, Instant.now()));
            } else {
                return AssertionOutcome.failure(
                        assertion.assertionType(),
                        assertion.toString(),
                        "Assertion returned false: " + assertion.toString(),
                        List.of(screenshot),
                        context.captureDom(),
                        Duration.between(startTime, Instant.now()));
            }

        } catch (Exception e) {
            try {
                String screenshot = context.captureScreenshot(assertion.assertionType().toLowerCase() + "-error");
                return AssertionOutcome.failure(
                        assertion.assertionType(),
                        assertion.toString(),
                        "Exception: " + e.getMessage(),
                        List.of(screenshot),
                        context.captureDom(),
                        Duration.between(startTime, Instant.now()));
            } catch (IOException ioE) {
                return AssertionOutcome.failure(
                        assertion.assertionType(),
                        assertion.toString(),
                        "Exception: " + e.getMessage(),
                        List.of(),
                        context.captureDom(),
                        Duration.between(startTime, Instant.now()));
            }
        }
    }

    /**
     * Format a stack trace array to a string.
     */
    private String formatStackTrace(StackTraceElement[] elements) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : elements) {
            sb.append(element).append("\n");
        }
        return sb.toString();
    }
}
