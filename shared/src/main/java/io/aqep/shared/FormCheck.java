package io.aqep.shared;

import java.util.List;

/** Auditable result for one form checked without triggering submission. */
public record FormCheck(
        int formNumber,
        int fieldsFilled,
        boolean valid,
        List<String> invalidControls,
        String screenshotPath,
        boolean submitted) {
    public FormCheck {
        if (formNumber < 1 || fieldsFilled < 0) throw new IllegalArgumentException("Invalid form check counts");
        invalidControls = List.copyOf(invalidControls);
        screenshotPath = screenshotPath == null ? "" : screenshotPath;
        if (valid != invalidControls.isEmpty()) throw new IllegalArgumentException("Form validity must match invalid controls");
        if (submitted) throw new IllegalArgumentException("AQEP never submits forms during pre-submit validation");
    }
}
