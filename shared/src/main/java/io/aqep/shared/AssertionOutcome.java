package io.aqep.shared;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Outcome of executing a single assertion.
 */
public record AssertionOutcome(
        String assertionType,
        String assertion,
        boolean success,
        Optional<String> failureReason,
        List<String> screenshotPaths,
        String domSnapshot,
        java.util.Map<String, Object> metadata,
        Duration duration
) {
    /**
     * Create a successful assertion outcome.
     */
    public static AssertionOutcome success(String assertionType, String assertion, List<String> screenshots,
            String dom, Duration duration) {
        return new AssertionOutcome(assertionType, assertion, true, Optional.empty(), screenshots, dom, java.util.Map.of(),
                duration);
    }

    /**
     * Create a failed assertion outcome.
     */
    public static AssertionOutcome failure(String assertionType, String assertion, String reason, List<String> screenshots,
            String dom, Duration duration) {
        return new AssertionOutcome(assertionType, assertion, false, Optional.of(reason), screenshots, dom, java.util.Map.of(),
                duration);
    }
}
