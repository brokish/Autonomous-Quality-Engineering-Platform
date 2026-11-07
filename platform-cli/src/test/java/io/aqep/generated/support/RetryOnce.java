package io.aqep.generated.support;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/** Retries a failed generated test once to absorb transient network or rendering flakiness. */
public final class RetryOnce implements IRetryAnalyzer {
    private boolean retried;

    @Override
    public boolean retry(ITestResult result) {
        if (retried) return false;
        retried = true;
        return true;
    }
}
