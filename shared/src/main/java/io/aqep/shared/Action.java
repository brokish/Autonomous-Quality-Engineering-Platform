package io.aqep.shared;

import java.util.Map;
import java.util.Optional;

/**
 * Sealed interface representing a testable action that can be executed on a web page.
 * Each action type is a specific record implementing this interface.
 */
public sealed interface Action permits
        Action.NavigateAction, Action.ClickAction, Action.FillAction, Action.SelectAction, Action.SearchAction,
        Action.DownloadAction, Action.SubmitAction, Action.HoverAction, Action.CheckboxAction, Action.CustomJsAction {

    String actionType();

    Map<String, Object> toMap();

    /**
     * Navigate to a URL.
     */
    record NavigateAction(String url, Optional<Long> timeoutMs) implements Action {
        public NavigateAction(String url) {
            this(url, Optional.empty());
        }

        @Override
        public String actionType() {
            return "NAVIGATE";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "NAVIGATE", "url", url, "timeout", timeoutMs.orElse(30000L));
        }
    }

    /**
     * Click an element at the given locator.
     */
    record ClickAction(String locator, Optional<Long> timeoutMs, Optional<String> buttonType) implements Action {
        public ClickAction(String locator) {
            this(locator, Optional.empty(), Optional.empty());
        }

        @Override
        public String actionType() {
            return "CLICK";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "CLICK", "locator", locator, "timeout", timeoutMs.orElse(10000L),
                    "button", buttonType.orElse("left"));
        }
    }

    /**
     * Fill an input field with text.
     */
    record FillAction(String locator, String value, Optional<Long> timeoutMs) implements Action {
        public FillAction(String locator, String value) {
            this(locator, value, Optional.empty());
        }

        @Override
        public String actionType() {
            return "FILL";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "FILL", "locator", locator, "value", value, "timeout", timeoutMs.orElse(10000L));
        }
    }

    /**
     * Select an option from a dropdown or select element.
     */
    record SelectAction(String locator, String value, Optional<Long> timeoutMs) implements Action {
        public SelectAction(String locator, String value) {
            this(locator, value, Optional.empty());
        }

        @Override
        public String actionType() {
            return "SELECT";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "SELECT", "locator", locator, "value", value, "timeout", timeoutMs.orElse(10000L));
        }
    }

    /**
     * Perform a search action: fill search input, submit, and wait for results.
     */
    record SearchAction(String searchInputLocator, String searchTerm, Optional<String> submitButtonLocator,
            Optional<Long> timeoutMs) implements Action {
        public SearchAction(String searchInputLocator, String searchTerm) {
            this(searchInputLocator, searchTerm, Optional.empty(), Optional.empty());
        }

        @Override
        public String actionType() {
            return "SEARCH";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "SEARCH", "input_locator", searchInputLocator, "search_term", searchTerm,
                    "submit_button", submitButtonLocator.orElse("optional"), "timeout", timeoutMs.orElse(15000L));
        }
    }

    /**
     * Trigger a download by clicking an element.
     */
    record DownloadAction(String triggerLocator, Optional<String> expectedFilePattern, Optional<Long> timeoutMs)
            implements Action {
        public DownloadAction(String triggerLocator) {
            this(triggerLocator, Optional.empty(), Optional.empty());
        }

        @Override
        public String actionType() {
            return "DOWNLOAD";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "DOWNLOAD", "trigger_locator", triggerLocator, "file_pattern",
                    expectedFilePattern.orElse("*"), "timeout", timeoutMs.orElse(30000L));
        }
    }

    /**
     * Submit a form element.
     */
    record SubmitAction(String formLocator, Optional<Long> timeoutMs) implements Action {
        public SubmitAction(String formLocator) {
            this(formLocator, Optional.empty());
        }

        @Override
        public String actionType() {
            return "SUBMIT";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "SUBMIT", "form_locator", formLocator, "timeout", timeoutMs.orElse(10000L));
        }
    }

    /**
     * Hover over an element.
     */
    record HoverAction(String locator, Optional<Long> timeoutMs) implements Action {
        public HoverAction(String locator) {
            this(locator, Optional.empty());
        }

        @Override
        public String actionType() {
            return "HOVER";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "HOVER", "locator", locator, "timeout", timeoutMs.orElse(5000L));
        }
    }

    /**
     * Check or uncheck a checkbox.
     */
    record CheckboxAction(String locator, boolean checked, Optional<Long> timeoutMs) implements Action {
        public CheckboxAction(String locator, boolean checked) {
            this(locator, checked, Optional.empty());
        }

        @Override
        public String actionType() {
            return "CHECKBOX";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "CHECKBOX", "locator", locator, "checked", checked, "timeout",
                    timeoutMs.orElse(5000L));
        }
    }

    /**
     * Execute custom JavaScript.
     */
    record CustomJsAction(String script, Optional<Long> timeoutMs) implements Action {
        public CustomJsAction(String script) {
            this(script, Optional.empty());
        }

        @Override
        public String actionType() {
            return "CUSTOM_JS";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "CUSTOM_JS", "script", script, "timeout", timeoutMs.orElse(5000L));
        }
    }
}
