package io.aqep.shared;

/**
 * What was observed for one executed journey step: the page it ran on, the page it led to and the
 * checks (nullable) that prove the step worked.
 */
public record StepTrace(String pageUrl, String resultUrl, String expectUrlPart, String expectTitlePart) {
}
