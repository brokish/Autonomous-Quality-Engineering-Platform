package io.aqep.shared;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Comprehensive result of executing a complete scenario.
 */
public record ExecutionResult(
        String scenarioName,
        boolean success,
        List<ActionOutcome> actionOutcomes,
        List<AssertionOutcome> assertionOutcomes,
        List<String> screenshotPaths,
        String consoleLogs,
        String networkLogs,
        String finalDomSnapshot,
        Optional<String> errorMessage,
        Optional<String> stackTrace,
        Duration totalExecutionTime,
        ExecutionMetadata metadata
) {
    /**
     * Check if all actions succeeded.
     */
    public boolean allActionsSucceeded() {
        return actionOutcomes.stream().allMatch(ActionOutcome::success);
    }

    /**
     * Check if all assertions succeeded.
     */
    public boolean allAssertionsSucceeded() {
        return assertionOutcomes.stream().allMatch(AssertionOutcome::success);
    }

    /**
     * Count failed actions.
     */
    public long failedActionCount() {
        return actionOutcomes.stream().filter(ao -> !ao.success()).count();
    }

    /**
     * Count failed assertions.
     */
    public long failedAssertionCount() {
        return assertionOutcomes.stream().filter(ao -> !ao.success()).count();
    }

    /**
     * Builder for ExecutionResult.
     */
    public static class Builder {
        private String scenarioName = "Unknown";
        private boolean success = false;
        private final List<ActionOutcome> actionOutcomes = new java.util.ArrayList<>();
        private final List<AssertionOutcome> assertionOutcomes = new java.util.ArrayList<>();
        private final List<String> screenshotPaths = new java.util.ArrayList<>();
        private String consoleLogs = "";
        private String networkLogs = "";
        private String finalDomSnapshot = "";
        private Optional<String> errorMessage = Optional.empty();
        private Optional<String> stackTrace = Optional.empty();
        private Duration totalExecutionTime = Duration.ZERO;
        private ExecutionMetadata metadata = new ExecutionMetadata(java.util.Map.of());

        public Builder scenarioName(String name) {
            this.scenarioName = name;
            return this;
        }

        public Builder success(boolean s) {
            this.success = s;
            return this;
        }

        public Builder addActionOutcome(ActionOutcome outcome) {
            this.actionOutcomes.add(outcome);
            return this;
        }

        public Builder addAssertionOutcome(AssertionOutcome outcome) {
            this.assertionOutcomes.add(outcome);
            return this;
        }

        public Builder addScreenshot(String path) {
            this.screenshotPaths.add(path);
            return this;
        }

        public Builder consoleLogs(String logs) {
            this.consoleLogs = logs;
            return this;
        }

        public Builder networkLogs(String logs) {
            this.networkLogs = logs;
            return this;
        }

        public Builder finalDom(String dom) {
            this.finalDomSnapshot = dom;
            return this;
        }

        public Builder error(String msg, String trace) {
            this.errorMessage = Optional.of(msg);
            this.stackTrace = Optional.of(trace);
            return this;
        }

        public Builder totalTime(Duration duration) {
            this.totalExecutionTime = duration;
            return this;
        }

        public Builder metadata(ExecutionMetadata m) {
            this.metadata = m;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(scenarioName, success, List.copyOf(actionOutcomes),
                    List.copyOf(assertionOutcomes), List.copyOf(screenshotPaths), consoleLogs, networkLogs,
                    finalDomSnapshot, errorMessage, stackTrace, totalExecutionTime, metadata);
        }
    }
}
