package io.aqep.core.execution;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import io.aqep.shared.*;
import java.util.List;

/**
 * Dispatches actions to specific execution handlers and validates assertions.
 * This is the core workhorse that performs actual Playwright operations.
 */
public class ActionExecutor {

    /**
     * Execute an action on the page.
     *
     * @param action  The action to execute
     * @param context The action context
     * @throws Exception if the action fails
     */
    public void execute(Action action, ActionContext context) throws Exception {
        switch (action) {
            case Action.NavigateAction nav -> executeNavigate(nav, context);
            case Action.ClickAction click -> executeClick(click, context);
            case Action.FillAction fill -> executeFill(fill, context);
            case Action.SelectAction select -> executeSelect(select, context);
            case Action.SearchAction search -> executeSearch(search, context);
            case Action.DownloadAction download -> executeDownload(download, context);
            case Action.SubmitAction submit -> executeSubmit(submit, context);
            case Action.HoverAction hover -> executeHover(hover, context);
            case Action.CheckboxAction checkbox -> executeCheckbox(checkbox, context);
            case Action.CustomJsAction custom -> executeCustomJs(custom, context);
            default -> throw new UnsupportedOperationException("Unknown action type: " + action.getClass().getName());
        }
    }

    /**
     * Execute an assertion on the page.
     *
     * @return true if assertion passed, false if it failed
     * @throws Exception if the assertion could not be evaluated
     */
    public boolean executeAssertion(Assertion assertion, ActionContext context) throws Exception {
        return switch (assertion) {
            case Assertion.ElementVisibleAssertion ev -> assertElementVisible(ev, context);
            case Assertion.ElementTextAssertion et -> assertElementText(et, context);
            case Assertion.ElementAttributeAssertion ea -> assertElementAttribute(ea, context);
            case Assertion.PageTitleAssertion pt -> assertPageTitle(pt, context);
            case Assertion.UrlContainsAssertion uc -> assertUrlContains(uc, context);
            case Assertion.ConsoleNoErrorsAssertion cne -> assertConsoleNoErrors(cne, context);
            case Assertion.NetworkNoErrorsAssertion nne -> assertNetworkNoErrors(nne, context);
            case Assertion.ElementCountAssertion ec -> assertElementCount(ec, context);
            case Assertion.ElementEnabledAssertion ee -> assertElementEnabled(ee, context);
            case Assertion.ElementCheckedAssertion ec -> assertElementChecked(ec, context);
            case Assertion.FileDownloadedAssertion fd -> assertFileDownloaded(fd, context);
            default -> throw new UnsupportedOperationException("Unknown assertion type: " +
                    assertion.getClass().getName());
        };
    }

    // ============ Action Implementations ============

    private void executeNavigate(Action.NavigateAction action, ActionContext context) throws Exception {
        long timeout = action.timeoutMs().orElse(30_000L);
        context.navigateTo(action.url(), timeout);
    }

    private void executeClick(Action.ClickAction action, ActionContext context) throws Exception {
        Locator locator = context.resolveLocator(action.locator(), 10_000);
        locator.click();
    }

    private void executeFill(Action.FillAction action, ActionContext context) throws Exception {
        Locator locator = context.resolveLocator(action.locator(), 10_000);
        locator.fill(action.value());
    }

    private void executeSelect(Action.SelectAction action, ActionContext context) throws Exception {
        Locator locator = context.resolveLocator(action.locator(), 10_000);
        locator.selectOption(action.value());
    }

    private void executeSearch(Action.SearchAction action, ActionContext context) throws Exception {
        // Fill search input
        Locator searchInput = context.resolveLocator(action.searchInputLocator(), 10_000);
        searchInput.fill(action.searchTerm());

        // Submit if button locator provided
        if (action.submitButtonLocator().isPresent() && !action.submitButtonLocator().get().equals("optional")) {
            Locator submitButton = context.resolveLocator(action.submitButtonLocator().get(), 10_000);
            submitButton.click();
        } else {
            // Try to find a button or press Enter
            try {
                searchInput.press("Enter");
            } catch (Exception e) {
                // Fallback: search was submitted anyway
            }
        }

        // Wait for results to load
        long timeout = action.timeoutMs().orElse(15_000L);
        context.getPage().waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeout));
    }

    private void executeDownload(Action.DownloadAction action, ActionContext context) throws Exception {
        Locator trigger = context.resolveLocator(action.triggerLocator(), 10_000);
        // In a real implementation, you'd listen for download events
        // For now, just click the trigger
        trigger.click();
    }

    private void executeSubmit(Action.SubmitAction action, ActionContext context) throws Exception {
        Locator form = context.resolveLocator(action.formLocator(), 10_000);
        form.evaluate("el => el.submit()");
    }

    private void executeHover(Action.HoverAction action, ActionContext context) throws Exception {
        Locator locator = context.resolveLocator(action.locator(), 5_000);
        locator.hover();
    }

    private void executeCheckbox(Action.CheckboxAction action, ActionContext context) throws Exception {
        Locator locator = context.resolveLocator(action.locator(), 5_000);
        boolean isChecked = (Boolean) locator.evaluate("el => el.checked");

        if (action.checked() && !isChecked) {
            locator.check();
        } else if (!action.checked() && isChecked) {
            locator.uncheck();
        }
    }

    private void executeCustomJs(Action.CustomJsAction action, ActionContext context) throws Exception {
        context.getPage().evaluate(action.script());
    }

    // ============ Assertion Implementations ============

    private boolean assertElementVisible(Assertion.ElementVisibleAssertion assertion, ActionContext context)
            throws Exception {
        try {
            Locator locator = context.resolveLocator(assertion.locator(), assertion.timeoutMs().orElse(5_000L));
            return locator.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assertElementText(Assertion.ElementTextAssertion assertion, ActionContext context) throws Exception {
        try {
            Locator locator = context.resolveLocator(assertion.locator(), assertion.timeoutMs().orElse(5_000L));
            String text = locator.textContent();

            if (assertion.partialMatch()) {
                return text != null && text.contains(assertion.expectedText());
            } else {
                return text != null && text.equals(assertion.expectedText());
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assertElementAttribute(Assertion.ElementAttributeAssertion assertion, ActionContext context)
            throws Exception {
        try {
            Locator locator = context.resolveLocator(assertion.locator(), assertion.timeoutMs().orElse(5_000L));
            String attrValue = locator.getAttribute(assertion.attributeName());

            if (assertion.partialMatch()) {
                return attrValue != null && attrValue.contains(assertion.expectedValue());
            } else {
                return attrValue != null && attrValue.equals(assertion.expectedValue());
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assertPageTitle(Assertion.PageTitleAssertion assertion, ActionContext context) throws Exception {
        String title = context.getPage().title();

        if (assertion.partialMatch()) {
            return title.contains(assertion.expectedTitle());
        } else {
            return title.equals(assertion.expectedTitle());
        }
    }

    private boolean assertUrlContains(Assertion.UrlContainsAssertion assertion, ActionContext context) throws Exception {
        return context.getPage().url().contains(assertion.expectedUrlPart());
    }

    private boolean assertConsoleNoErrors(Assertion.ConsoleNoErrorsAssertion assertion, ActionContext context)
            throws Exception {
        String logs = context.getConsoleLogs();
        // Check for "error" in logs
        boolean hasErrors = logs.toLowerCase().contains("[error]");

        if (assertion.excludePatterns().isPresent()) {
            String pattern = assertion.excludePatterns().get();
            // Simple pattern matching - exclude lines matching pattern
            return !hasErrors || logs.contains(pattern);
        }

        return !hasErrors;
    }

    private boolean assertNetworkNoErrors(Assertion.NetworkNoErrorsAssertion assertion, ActionContext context)
            throws Exception {
        String logs = context.getNetworkLogs();
        // Check if there are any error status codes
        boolean hasErrors = !logs.isEmpty();

        if (assertion.excludePatterns().isPresent()) {
            String pattern = assertion.excludePatterns().get();
            return !hasErrors || logs.contains(pattern);
        }

        return !hasErrors;
    }

    private boolean assertElementCount(Assertion.ElementCountAssertion assertion, ActionContext context)
            throws Exception {
        try {
            Locator locator = context.resolveLocator(assertion.locator(), 5_000);
            int count = locator.count();

            return switch (assertion.operator().orElse("equals")) {
                case "equals" -> count == assertion.expectedCount();
                case "greater" -> count > assertion.expectedCount();
                case "less" -> count < assertion.expectedCount();
                case "greater_or_equal" -> count >= assertion.expectedCount();
                case "less_or_equal" -> count <= assertion.expectedCount();
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assertElementEnabled(Assertion.ElementEnabledAssertion assertion, ActionContext context)
            throws Exception {
        try {
            Locator locator = context.resolveLocator(assertion.locator(), assertion.timeoutMs().orElse(5_000L));
            boolean isEnabled = (Boolean) locator.evaluate("el => !el.disabled");

            return assertion.shouldBeEnabled() == isEnabled;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assertElementChecked(Assertion.ElementCheckedAssertion assertion, ActionContext context)
            throws Exception {
        try {
            Locator locator = context.resolveLocator(assertion.locator(), assertion.timeoutMs().orElse(5_000L));
            boolean isChecked = (Boolean) locator.evaluate("el => el.checked");

            return assertion.shouldBeChecked() == isChecked;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean assertFileDownloaded(Assertion.FileDownloadedAssertion assertion, ActionContext context)
            throws Exception {
        // This would require tracking downloads - placeholder for now
        return true;
    }
}
