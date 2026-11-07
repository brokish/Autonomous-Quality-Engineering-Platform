package io.aqep.generated.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.aqep.core.execution.ActionContext;
import io.aqep.healing.LocatorMemory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;

/**
 * Base class of every generated test class. Each test method gets its own isolated browser context, so any
 * generated test can run alone (IDE gutter, -Dtest=..., or a suite XML) without depending on another test.
 */
public abstract class GeneratedTestBase {
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    protected ActionContext ctx;

    @BeforeClass(alwaysRun = true)
    @Parameters({"headless"})
    public void launchBrowser(@org.testng.annotations.Optional("true") String headless) {
        boolean isHeadless = Boolean.parseBoolean(System.getProperty("aqep.headless", headless));
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(isHeadless));
    }

    @BeforeMethod(alwaysRun = true)
    public void openIsolatedContext() throws java.io.IOException {
        context = browser.newContext();
        Page page = context.newPage();
        ctx = new ActionContext(page, context, Path.of("artifacts", "generated-tests"), Optional.empty())
                .withHealing(null, LocatorMemory.forCurrentUser());
    }

    @AfterMethod(alwaysRun = true)
    public void closeContext(ITestResult result) throws Exception {
        if (ctx != null && !result.isSuccess() && result.getStatus() != ITestResult.SKIP) {
            Path directory = Path.of("artifacts", "generated-tests", "failures");
            Files.createDirectories(directory);
            ctx.getPage().screenshot(new Page.ScreenshotOptions()
                    .setPath(directory.resolve(result.getTestClass().getRealClass().getSimpleName() + "-"
                            + result.getMethod().getMethodName() + ".png")));
        }
        if (context != null) context.close();
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
