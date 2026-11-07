package io.aqep.shared;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Outcome of executing a single action.
 */
public record ActionOutcome(
        String actionType,
        String action,
        boolean success,
        Optional<String> errorMessage,
        Optional<String> stackTrace,
        List<String> screenshotPaths,
        String domSnapshot,
        java.util.Map<String, Object> metadata,
        Duration duration
) {
    /**
     * Create a successful action outcome.
     */
    public static ActionOutcome success(String actionType, String action, List<String> screenshots, String dom,
            Duration duration) {
        return new ActionOutcome(actionType, action, true, Optional.empty(), Optional.empty(), screenshots, dom,
                java.util.Map.of(), duration);
    }

    /**
     * Create a failed action outcome.
     */
    public static ActionOutcome failure(String actionType, String action, String errorMsg, String stackTrace,
            List<String> screenshots, String dom, Duration duration) {
        return new ActionOutcome(actionType, action, false, Optional.of(errorMsg), Optional.of(stackTrace), screenshots,
                dom, java.util.Map.of(), duration);
    }
}
