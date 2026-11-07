package io.aqep.shared;

import java.util.Map;
import java.util.Optional;

/**
 * Sealed interface representing a verifiable assertion about page state or element properties.
 * Each assertion type is a specific record implementing this interface.
 */
public sealed interface Assertion permits
        Assertion.ElementVisibleAssertion, Assertion.ElementTextAssertion, Assertion.ElementAttributeAssertion, Assertion.PageTitleAssertion,
        Assertion.UrlContainsAssertion, Assertion.ConsoleNoErrorsAssertion, Assertion.NetworkNoErrorsAssertion, Assertion.ElementCountAssertion,
        Assertion.ElementEnabledAssertion, Assertion.ElementCheckedAssertion, Assertion.FileDownloadedAssertion {

    String assertionType();

    Map<String, Object> toMap();

    /**
     * Assert that an element is visible on the page.
     */
    record ElementVisibleAssertion(String locator, Optional<Long> timeoutMs) implements Assertion {
        public ElementVisibleAssertion(String locator) {
            this(locator, Optional.empty());
        }

        @Override
        public String assertionType() {
            return "ELEMENT_VISIBLE";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "ELEMENT_VISIBLE", "locator", locator, "timeout", timeoutMs.orElse(5000L));
        }
    }

    /**
     * Assert that an element contains specific text.
     */
    record ElementTextAssertion(String locator, String expectedText, boolean partialMatch, Optional<Long> timeoutMs)
            implements Assertion {
        public ElementTextAssertion(String locator, String expectedText) {
            this(locator, expectedText, false, Optional.empty());
        }

        public ElementTextAssertion(String locator, String expectedText, boolean partialMatch) {
            this(locator, expectedText, partialMatch, Optional.empty());
        }

        @Override
        public String assertionType() {
            return "ELEMENT_TEXT";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "ELEMENT_TEXT", "locator", locator, "expected_text", expectedText,
                    "partial_match", partialMatch, "timeout", timeoutMs.orElse(5000L));
        }
    }

    /**
     * Assert that an element has a specific attribute value.
     */
    record ElementAttributeAssertion(String locator, String attributeName, String expectedValue, boolean partialMatch,
            Optional<Long> timeoutMs) implements Assertion {
        public ElementAttributeAssertion(String locator, String attributeName, String expectedValue) {
            this(locator, attributeName, expectedValue, false, Optional.empty());
        }

        @Override
        public String assertionType() {
            return "ELEMENT_ATTRIBUTE";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "ELEMENT_ATTRIBUTE", "locator", locator, "attribute", attributeName,
                    "expected_value", expectedValue, "partial_match", partialMatch, "timeout",
                    timeoutMs.orElse(5000L));
        }
    }

    /**
     * Assert that the page title matches an expected value.
     */
    record PageTitleAssertion(String expectedTitle, boolean partialMatch) implements Assertion {
        public PageTitleAssertion(String expectedTitle) {
            this(expectedTitle, false);
        }

        @Override
        public String assertionType() {
            return "PAGE_TITLE";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "PAGE_TITLE", "expected_title", expectedTitle, "partial_match", partialMatch);
        }
    }

    /**
     * Assert that the page URL contains a specific substring.
     */
    record UrlContainsAssertion(String expectedUrlPart) implements Assertion {
        @Override
        public String assertionType() {
            return "URL_CONTAINS";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "URL_CONTAINS", "expected_url_part", expectedUrlPart);
        }
    }

    /**
     * Assert that the browser console has no error messages.
     */
    record ConsoleNoErrorsAssertion(Optional<String> excludePatterns) implements Assertion {
        public ConsoleNoErrorsAssertion() {
            this(Optional.empty());
        }

        @Override
        public String assertionType() {
            return "CONSOLE_NO_ERRORS";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "CONSOLE_NO_ERRORS", "exclude_patterns", excludePatterns.orElse(""));
        }
    }

    /**
     * Assert that the network has no failed requests.
     */
    record NetworkNoErrorsAssertion(Optional<String> excludePatterns) implements Assertion {
        public NetworkNoErrorsAssertion() {
            this(Optional.empty());
        }

        @Override
        public String assertionType() {
            return "NETWORK_NO_ERRORS";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "NETWORK_NO_ERRORS", "exclude_patterns", excludePatterns.orElse(""));
        }
    }

    /**
     * Assert that a certain number of elements match a locator.
     */
    record ElementCountAssertion(String locator, int expectedCount, Optional<String> operator) implements Assertion {
        public ElementCountAssertion(String locator, int expectedCount) {
            this(locator, expectedCount, Optional.empty());
        }

        @Override
        public String assertionType() {
            return "ELEMENT_COUNT";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "ELEMENT_COUNT", "locator", locator, "expected_count", expectedCount, "operator",
                    operator.orElse("equals"));
        }
    }

    /**
     * Assert that an element is enabled/disabled.
     */
    record ElementEnabledAssertion(String locator, boolean shouldBeEnabled, Optional<Long> timeoutMs)
            implements Assertion {
        public ElementEnabledAssertion(String locator, boolean shouldBeEnabled) {
            this(locator, shouldBeEnabled, Optional.empty());
        }

        @Override
        public String assertionType() {
            return "ELEMENT_ENABLED";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "ELEMENT_ENABLED", "locator", locator, "should_be_enabled", shouldBeEnabled,
                    "timeout", timeoutMs.orElse(5000L));
        }
    }

    /**
     * Assert that a checkbox/radio is in a specific state.
     */
    record ElementCheckedAssertion(String locator, boolean shouldBeChecked, Optional<Long> timeoutMs)
            implements Assertion {
        public ElementCheckedAssertion(String locator, boolean shouldBeChecked) {
            this(locator, shouldBeChecked, Optional.empty());
        }

        @Override
        public String assertionType() {
            return "ELEMENT_CHECKED";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "ELEMENT_CHECKED", "locator", locator, "should_be_checked", shouldBeChecked,
                    "timeout", timeoutMs.orElse(5000L));
        }
    }

    /**
     * Assert that a file was downloaded and exists.
     */
    record FileDownloadedAssertion(Optional<String> fileNamePattern) implements Assertion {
        public FileDownloadedAssertion() {
            this(Optional.empty());
        }

        @Override
        public String assertionType() {
            return "FILE_DOWNLOADED";
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("type", "FILE_DOWNLOADED", "file_pattern", fileNamePattern.orElse("*"));
        }
    }
}
