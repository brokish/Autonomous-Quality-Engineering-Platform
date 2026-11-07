package io.aqep.core;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import io.aqep.shared.Finding;
import io.aqep.shared.FormCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fills synthetic values and checks browser validity without clicking or submitting forms. */
public final class FormValidationExecutor {
    private static final int MAX_FORMS = 20;
    private static final int MAX_FIELDS_PER_FORM = 30;

    public FormValidationOutcome validate(Page page, String pageUrl, Path evidenceDirectory) {
        List<Finding> findings = new ArrayList<>();
        List<FormCheck> checks = new ArrayList<>();
        int formCount = Math.min(page.locator("form").count(), MAX_FORMS);
        if (formCount == 0) return new FormValidationOutcome(checks, findings);

        // Guard both normal submit events and legacy form.submit() while synthetic data is entered.
        page.evaluate("""
() => {
          for (const form of document.forms) form.addEventListener('submit', event => {
            event.preventDefault(); event.stopImmediatePropagation();
          }, true);
          HTMLFormElement.prototype.submit = function() {};
        }
""");

        for (int formIndex = 0; formIndex < formCount; formIndex++) {
            Locator form = page.locator("form").nth(formIndex);
            List<String> problems = new ArrayList<>();
            Locator fields = form.locator("input, textarea, select");
            int fieldCount = Math.min(fields.count(), MAX_FIELDS_PER_FORM);
            int fieldsFilled = 0;
            for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                Locator field = fields.nth(fieldIndex);
                try {
                    if (!field.isVisible() || !field.isEnabled()) continue;
                    Object raw = field.evaluate("el => ({tag:el.tagName.toLowerCase(), type:(el.type||'').toLowerCase(), " +
                            "name:el.name||'', placeholder:el.placeholder||'', aria:el.getAttribute('aria-label')||'', " +
                            "autocomplete:el.autocomplete||'', readonly:el.readOnly||false})");
                    if (!(raw instanceof Map<?, ?> attributes)) continue;
                    String tag = value(attributes.get("tag"));
                    String type = value(attributes.get("type"));
                    String label = String.join(" ", value(attributes.get("aria")), value(attributes.get("name")),
                            value(attributes.get("placeholder")), value(attributes.get("autocomplete")));
                    if (Boolean.TRUE.equals(attributes.get("readonly")) || skipType(type)) continue;

                    String synthetic = syntheticValue(tag, type, label, field);
                    if (synthetic.isBlank()) continue;
                    if ("select".equals(tag)) field.selectOption(synthetic);
                    else field.fill(synthetic);
                    field.blur();
                    fieldsFilled++;
                } catch (PlaywrightException exception) {
                    problems.add("field " + (fieldIndex + 1) + " could not be safely filled");
                }
            }

            Object invalid = form.evaluate("form => Array.from(form.elements).filter(el => " +
                    "!el.disabled && el.willValidate && (!el.validity.valid || el.getAttribute('aria-invalid') === 'true'))" +
                    ".map(el => ({name:el.name||el.id||el.tagName.toLowerCase(), " +
                    "message:el.validationMessage||el.getAttribute('aria-errormessage')||'invalid'}))");
            if (invalid instanceof List<?> invalidFields) {
                for (Object entry : invalidFields.stream().limit(20).toList()) {
                    if (entry instanceof Map<?, ?> detail) {
                        problems.add(value(detail.get("name")) + ": " + value(detail.get("message")));
                    }
                }
            }

            String screenshotPath = "";
            try {
                Files.createDirectories(evidenceDirectory);
                Path screenshot = evidenceDirectory.resolve(String.format("form-%02d-prefill.png", formIndex + 1));
                page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true));
                screenshotPath = screenshot.toString();
            } catch (Exception ignored) {
                // The validation outcome is still useful if screenshot capture is unavailable.
            }
            List<String> invalidControls = problems.stream().distinct().toList();
            boolean valid = invalidControls.isEmpty();
            checks.add(new FormCheck(formIndex + 1, fieldsFilled, valid, invalidControls, screenshotPath, false));
            if (!valid) {
                String evidence = String.join("; ", invalidControls);
                if (!screenshotPath.isBlank()) evidence += "; pre-submit screenshot: " + screenshotPath;
                findings.add(new Finding("form-validation", Finding.Severity.MEDIUM, pageUrl,
                        "Form remains invalid after safe pre-submit validation", evidence));
            }
        }
        return new FormValidationOutcome(checks, findings);
    }

    private String syntheticValue(String tag, String type, String label, Locator field) {
        if ("select".equals(tag)) {
            Object value = field.evaluate("el => { const option = Array.from(el.options).find(item => " +
                    "!item.disabled && item.value && !item.hidden); return option ? option.value : ''; }");
            return value == null ? "" : String.valueOf(value);
        }
        return switch (type) {
            case "hidden", "submit", "button", "reset", "image", "file", "checkbox", "radio", "range", "color" -> "";
            case "email" -> "aqep@example.invalid";
            case "url" -> "https://example.invalid";
            case "tel" -> "+12025550123";
            case "number" -> field.evaluate("el => el.min || '1'").toString();
            case "date" -> field.evaluate("el => el.min || '2000-01-01'").toString();
            case "time" -> field.evaluate("el => el.min || '12:00'").toString();
            case "month" -> field.evaluate("el => el.min || '2000-01'").toString();
            case "week" -> field.evaluate("el => el.min || '2000-W01'").toString();
            case "datetime-local" -> field.evaluate("el => el.min || '2000-01-01T12:00'").toString();
            default -> syntheticText(label);
        };
    }

    private String syntheticText(String label) {
        String hint = label.toLowerCase();
        if (hint.contains("search") || hint.contains("query")) return "aqep quality validation";
        if (hint.contains("email")) return "aqep@example.invalid";
        if (hint.contains("password")) return "AqepSafeTest42!";
        if (hint.contains("first") || hint.contains("given-name")) return "Aqep";
        if (hint.contains("last") || hint.contains("family-name")) return "Test";
        if (hint.contains("phone")) return "+12025550123";
        if (hint.contains("address")) return "1 Test Street";
        if (hint.contains("postal") || hint.contains("zip")) return "12345";
        if (hint.contains("company")) return "AQEP Test";
        if (hint.contains("name")) return "AQEP Test";
        return "AQEP validation";
    }

    private boolean skipType(String type) {
        return List.of("hidden", "submit", "button", "reset", "image", "file", "checkbox", "radio", "range", "color")
                .contains(type);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
