# Phase 1: Autonomous Scenario Execution Engine

**Objective:** Enable the platform to execute AI-generated test scenarios end-to-end.

## Architecture Overview

```
┌─────────────────────────────────────┐
│  QualityScenario (AI-Generated)     │  From OllamaQualityAgent
├─────────────────────────────────────┤
│         ScenarioExecutor            │  Converts scenario → actions
├─────────────────────────────────────┤
│  Action Abstraction Layer           │
│  ├─ NavigateAction                  │
│  ├─ FillFormAction                  │
│  ├─ ClickAction                     │
│  ├─ SelectAction                    │
│  ├─ SubmitAction                    │
│  ├─ SearchAction                    │
│  ├─ DownloadAction                  │
│  └─ VerifyAction (Assertion)        │
├─────────────────────────────────────┤
│  ActionExecutor (Playwright-backed) │
├─────────────────────────────────────┤
│         Playwright Browser          │
└─────────────────────────────────────┘

ExecutionResult ← Captures:
  - Action sequence
  - Screenshots
  - Assertion results
  - Console/network logs
  - DOM snapshots
  - Errors/stack traces
```

## Phase 1 Deliverables

### 1.1 Shared Data Models (shared module)

**File:** `shared/src/main/java/io/aqep/shared/Action.java`
- Sealed interface/enum for all action types
- Each action has:
  - Type (enum)
  - Target (locator or URL)
  - Value (input data)
  - Options (timeout, verification)

**File:** `shared/src/main/java/io/aqep/shared/Assertion.java`
- Sealed interface/enum for assertions
- Assertion types:
  - ElementVisible
  - ElementText
  - ElementAttribute
  - PageTitle
  - UrlContains
  - ConsoleNoErrors
  - NetworkNoErrors

**File:** `shared/src/main/java/io/aqep/shared/ExecutionResult.java`
- Success/failure status
- Actions executed + outcomes
- Assertion results + failures
- Screenshots captured
- Console/network logs
- Failure reason + stack trace
- Execution duration

### 1.2 Core Execution Engine (core-engine module)

**File:** `core-engine/src/main/java/io/aqep/core/execution/ActionContext.java`
- Holds Playwright Page, BrowserContext, test data
- Provides utility methods for locator resolution
- Tracks screenshots and logs

**File:** `core-engine/src/main/java/io/aqep/core/execution/ScenarioExecutor.java`
- Main executor: `ExecutionResult execute(QualityScenario, ActionContext)`
- Parses scenario preconditions, actions, assertions
- Executes each action with self-healing retry
- Verifies each assertion
- Captures evidence (screenshots, logs)

**File:** `core-engine/src/main/java/io/aqep/core/execution/ActionExecutor.java`
- Factory/dispatcher for action types
- Execution logic for each action type

**File:** `core-engine/src/main/java/io/aqep/core/execution/SearchFlowExecutor.java`
- Specialized: search input + submission + result validation
- Handles multi-step search flows

**File:** `core-engine/src/main/java/io/aqep/core/execution/DownloadFlowExecutor.java`
- Specialized: download initiation + file verification
- Tracks downloaded files
- Cleans up after verification

**File:** `core-engine/src/main/java/io/aqep/core/execution/NavigationFlowExecutor.java`
- Breadcrumb navigation validation
- Back/forward button handling

## Implementation Steps

### Step 1: Create Shared Action/Assertion Models

```java
// shared/src/main/java/io/aqep/shared/Action.java
public sealed interface Action permits NavigateAction, ClickAction, FillAction, ... {
    String actionType();
    Map<String, Object> toMap();
}

// Concrete actions
public record NavigateAction(String url) implements Action { ... }
public record ClickAction(String locator, Map<String, Object> options) implements Action { ... }
public record FillAction(String locator, String value) implements Action { ... }
public record SelectAction(String locator, String value) implements Action { ... }
public record SearchAction(String searchInputLocator, String searchTerm) implements Action { ... }
public record DownloadAction(String triggerLocator) implements Action { ... }
// etc.
```

### Step 2: Create ExecutionResult Model

```java
// shared/src/main/java/io/aqep/shared/ExecutionResult.java
public record ExecutionResult(
    boolean success,
    String scenario,
    List<ActionOutcome> actionOutcomes,
    List<AssertionOutcome> assertionOutcomes,
    List<String> screenshotPaths,
    String consoleLogs,
    String networkLogs,
    String domSnapshot,
    Optional<String> errorMessage,
    Optional<String> stackTrace,
    Duration executionTime
) { }
```

### Step 3: Implement ActionContext

```java
// core-engine/src/main/java/io/aqep/core/execution/ActionContext.java
public class ActionContext {
    private final Page page;
    private final BrowserContext browserContext;
    private final Path screenshotDir;
    private final SelfHealingLocator selfHealing;
    private final List<String> capturedScreenshots;
    
    public Locator resolveLocator(String locator) throws LocatorException { ... }
    public void takeScreenshot(String name) { ... }
    public String captureConsoleLogs() { ... }
    public String captureNetworkLogs() { ... }
    public String captureDom() { ... }
}
```

### Step 4: Implement ScenarioExecutor

Core logic flow:
```java
public ExecutionResult execute(QualityScenario scenario, ActionContext context) {
    // 1. Verify preconditions
    // 2. For each action:
    //    a. Execute with self-healing retry (2-3 attempts)
    //    b. Capture screenshot
    //    c. Record outcome
    // 3. For each assertion:
    //    a. Execute
    //    b. Record success/failure
    // 4. Return ExecutionResult
}
```

### Step 5: Specialized Executors

- **SearchFlowExecutor**: Navigate → Find search input → Fill → Submit → Verify results appear
- **DownloadFlowExecutor**: Click trigger → Wait for download → Verify file → Cleanup
- **NavigationFlowExecutor**: Follow breadcrumbs or navigation menu

## Integration Points

### With SelfHealingLocator (Phase 2)
```java
// In ActionExecutor, when executing ClickAction:
try {
    page.click(locator);
} catch (PlaywrightException e) {
    Optional<String> healedLocator = selfHealing.suggestAlternative(locator, domSnapshot, screenshot);
    if (healedLocator.isPresent()) {
        page.click(healedLocator.get());  // Retry with healed locator
    } else {
        throw e;
    }
}
```

### With CLI (Phase 8)
```java
// New command in AqepApplication:
// aqep <url> --execute-scenarios <scenario-json>
```

### With Reporting (Phase 7)
```java
// QualityReportWriter includes ExecutionResult details:
// - Test case outcomes per scenario
// - Failed assertion details
// - Screenshot timeline
```

## Testing Strategy

### Unit Tests
- ActionContext locator resolution
- Each action type execution
- Assertion validation
- Error scenarios (timeouts, missing elements)

### Integration Tests (with Testcontainers)
- Real browser scenarios against test pages
- Download flow validation
- Search flow validation
- Network/console log capture

### Test Pages
Create local test HTML pages in `platform-cli/test-pages/`:
- `search.html` — search form + results
- `download.html` — download link + verification
- `navigation.html` — breadcrumbs + menus
- `forms.html` — various form types
- `errors.html` — error scenarios

## Code Structure

```
core-engine/src/main/java/io/aqep/core/execution/
├── ActionContext.java
├── ActionExecutor.java
├── ScenarioExecutor.java
├── SearchFlowExecutor.java
├── DownloadFlowExecutor.java
└── NavigationFlowExecutor.java

shared/src/main/java/io/aqep/shared/
├── Action.java (sealed interface + records)
├── Assertion.java (sealed interface + records)
├── ActionOutcome.java
├── AssertionOutcome.java
└── ExecutionResult.java

platform-cli/src/test/resources/test-pages/
├── search.html
├── download.html
├── navigation.html
├── forms.html
└── errors.html
```

## Success Criteria

- [x] All action types can execute without errors
- [x] Assertions validate correctly
- [x] Screenshots capture at each step
- [x] Console/network logs are collected
- [x] Error handling is robust (timeouts, missing elements)
- [x] Self-healing integration is ready (Phase 2)
- [x] Integration tests pass on test pages
- [x] ExecutionResult can be consumed by reporters

## Timeline Estimate

- Models & interfaces: 4 hours
- ActionContext: 3 hours
- ScenarioExecutor core: 5 hours
- Specialized executors: 4 hours
- Integration tests: 4 hours
- Refinement & debugging: 2 hours

**Total: 22 hours (can parallelize some components)**

---

## Next Phase Dependency

Phase 2 (Self-Healing Integration) depends on:
- ✅ ActionContext complete
- ✅ ScenarioExecutor complete
- ✅ Action execution error handling in place

Once Phase 1 is merged, Phase 2 can add healing retry logic on top of stable executor.
