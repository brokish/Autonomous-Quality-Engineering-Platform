package io.aqep.shared;

import java.util.List;
import java.util.Optional;

/**
 * An executable test scenario with actions and assertions.
 * This extends the proposal concept (QualityScenario) with specific steps and validations.
 */
public record ExecutableScenario(
        String name,
        String targetPage,
        List<Action> preconditions,
        List<Action> actions,
        List<Assertion> assertions,
        boolean stopOnActionFailure,
        Optional<String> description,
        List<StepTrace> trace
) {
    public ExecutableScenario {
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    /** @param trace one entry per action: where it ran, where it led and how to verify it. */
    public ExecutableScenario(String name, String targetPage, List<Action> preconditions, List<Action> actions,
            List<Assertion> assertions, boolean stopOnActionFailure, Optional<String> description) {
        this(name, targetPage, preconditions, actions, assertions, stopOnActionFailure, description, List.of());
    }

    public ExecutableScenario(String name, String targetPage, List<Action> actions, List<Assertion> assertions) {
        this(name, targetPage, List.of(), actions, assertions, true, Optional.empty());
    }

    public ExecutableScenario(String name, List<Action> actions, List<Assertion> assertions) {
        this(name, "", List.of(), actions, assertions, true, Optional.empty());
    }
}
