package io.aqep.core;

import io.aqep.shared.Finding;
import io.aqep.shared.FormCheck;
import java.util.List;

public record FormValidationOutcome(List<FormCheck> checks, List<Finding> findings) {
    public FormValidationOutcome {
        checks = List.copyOf(checks);
        findings = List.copyOf(findings);
    }
}
