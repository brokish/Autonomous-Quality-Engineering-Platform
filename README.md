# Autonomous Quality Engineering Platform

AQEP analyzes public websites without requiring authored test cases. It crawls a bounded number of same-origin pages with Playwright, captures screenshots and browser errors, checks common accessibility and UI-quality signals, and writes JSON, HTML, and Allure-compatible reports. Optional local Ollama models provide root-cause and screenshot observations.

## Requirements

- Java 21 and Maven 3.9+
- TestNG (managed by Maven)
- Chromium installed for Playwright
- Optional: local Ollama with an approved text model and `llava`
- Optional for the gated container integration test: Docker

## Build and run

```powershell
mvn -B verify
mvn -B -DskipTests install
mvn -pl platform-cli exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
mvn -pl platform-cli exec:java -Dexec.args="https://example.com --no-ai --out artifacts"
```

The command accepts `--max-pages`, `--timeout-seconds`, `--out`, `--ollama`, `--text-model`, `--vision-model`, and `--no-ai`. Page count defaults to 10 and is capped at 100. AI defaults to the local Ollama endpoint at `http://localhost:11434`. Supported text models are `qwen2.5-coder:7b`, `qwen2.5-coder:14b`, and `deepseek-r1`; the vision model is `llava`. Install models with `ollama pull qwen2.5-coder:7b` and `ollama pull llava`. Remote AI endpoints and unapproved model names are rejected. The AI integration is optional; local endpoint/model errors appear in the report instead of preventing the browser scan.

Reports are written to `artifacts/summary.html`, `artifacts/analysis.json`, `artifacts/allure-results/`, and `artifacts/screenshots/`. To render the Allure results, install the Allure CLI and run `allure generate artifacts/allure-results --clean -o artifacts/allure-report`.

The test suite uses TestNG with the Allure TestNG adapter; its suite is defined in `platform-cli/src/test/resources/testng.xml`, and test classes live in `platform-cli/src/test/java`. Run it directly from the project root with `mvn -B -pl platform-cli -am test`; `mvn -B verify` also uses this suite.

To scan a website as a TestNG test, set `targetUrl` in `platform-cli/src/test/resources/website-analysis.xml` and run:

```powershell
mvn -B -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/website-analysis.xml
```

Alternatively, pass the URL without editing the suite using `'-Daqep.url=https://your-public-site.example'`. This scan uses Playwright Chromium and writes its report to `platform-cli/artifacts/summary.html`; AI enrichment is disabled for this test.
The TestNG run shows separate checks for crawling, page metadata, screenshots, accessibility, UI findings, browser diagnostics, and report generation. Findings are reported as results rather than automatically failing the run; inspect the HTML/JSON report for the actual issues.

## Docker

```powershell
docker compose build aqep
docker compose up -d ollama
docker compose exec ollama ollama pull qwen2.5-coder:7b
docker compose exec ollama ollama pull llava
docker compose --profile analysis run --rm aqep https://example.com
```

`artifacts/` is mounted from the host. Ollama models persist in a named volume. The browser container makes outbound requests, so only scan authorized public targets and apply your organization’s egress policy.

Run the optional Ollama Testcontainers smoke test in PowerShell when Docker is available:

```powershell
$env:AQEP_RUN_CONTAINER_TESTS = "true"
mvn -B verify
```

## Safety and scope

Only HTTP(S) URLs with publicly-resolving hosts are accepted. Private, loopback, link-local, and local hostnames are rejected; cross-origin navigations are blocked; resource requests resolving to private addresses are also blocked. The crawl does not click, submit, authenticate, or mutate target sites. A target can still serve large or hostile content, so run scans with an appropriate timeout, page cap, and network isolation.

Self-healing recipes use role, label, placeholder, text, and test-id locators, not generated XPath. The Ollama agent can suggest validated semantic recipes from a DOM snapshot. Callers compose these suggestions with `SelfHealingLocator`; successful replacements are stored in the user's `.aqep/locator-memory.json` via `LocatorMemory.forCurrentUser()`. Avoid sharing this file across sites or tenants with different trust boundaries.

See [architecture](docs/architecture.md) for module responsibilities and data flow.
