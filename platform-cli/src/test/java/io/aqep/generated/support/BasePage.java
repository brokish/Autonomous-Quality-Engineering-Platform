package io.aqep.generated.support;

import io.aqep.core.execution.ActionContext;
import io.aqep.core.execution.ActionExecutor;
import io.aqep.shared.Action;
import io.aqep.shared.Assertion;
import org.testng.Assert;

/** Base of all generated page objects. Wraps the engine so pages only express user intent. */
public abstract class BasePage {
    private static final String BASE_URL_OVERRIDE = "aqep.generated.baseUrl";
    private final ActionExecutor executor = new ActionExecutor();
    protected final ActionContext ctx;

    protected BasePage(ActionContext ctx) {
        this.ctx = ctx;
    }

    /** Opens the page; -Daqep.generated.baseUrl=https://staging.example.com replaces the recorded origin. */
    protected void open(String url) {
        String override = System.getProperty(BASE_URL_OVERRIDE);
        String target = override == null || override.isBlank() ? url
                : override.replaceAll("/+$", "") + url.replaceFirst("^https?://[^/]+", "");
        run(new Action.NavigateAction(target));
    }

    protected void click(String locator) {
        run(new Action.ClickAction(locator));
    }

    protected void search(String locator, String term) {
        run(new Action.SearchAction(locator, term));
    }

    public void assertUrlContains(String expected) {
        check(new Assertion.UrlContainsAssertion(expected), "URL should contain '" + expected + "'");
    }

    public void assertTitleContains(String expected) {
        check(new Assertion.PageTitleAssertion(expected, true), "Title should contain '" + expected + "'");
    }

    protected void assertVisible(String locator) {
        check(new Assertion.ElementVisibleAssertion(locator), "Element should be visible: " + locator);
    }

    private void run(Action action) {
        try {
            executor.execute(action, ctx);
        } catch (Exception exception) {
            throw new AssertionError("Action failed: " + action + " - " + exception.getMessage(), exception);
        }
    }

    private void check(Assertion assertion, String message) {
        try {
            Assert.assertTrue(executor.executeAssertion(assertion, ctx), message);
        } catch (AssertionError error) {
            throw error;
        } catch (Exception exception) {
            throw new AssertionError(message + " (" + exception.getMessage() + ")", exception);
        }
    }
}
