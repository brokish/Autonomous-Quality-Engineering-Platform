# Autonomous Quality Engineering Platform

AQEP scans public websites without requiring authored test cases. It uses Playwright to crawl a bounded number of same-origin pages, capture screenshots and browser diagnostics, and report common UI and accessibility findings. Optional AI enrichment runs locally through Ollama; no paid AI service or API key is used.

## Requirements

- Git
- Java 21 JDK (including `javac`)
- Maven 3.9 or newer (Maven is not bundled in this repository)
- Internet access for the initial Maven dependency and Playwright browser downloads
- Optional: Ollama and the approved local models for AI analysis
- Optional: Docker for the Ollama Testcontainers smoke test or Docker Compose workflow

## Clone and build

Run these commands from PowerShell:

```powershell
git clone https://github.com/brokish/Autonomous-Quality-Engineering-Platform.git
Set-Location Autonomous-Quality-Engineering-Platform
java -version
mvn -version
mvn -B verify
```

The default TestNG suite is `platform-cli/src/test/resources/testng.xml`. `mvn -B verify` builds all Maven modules and runs this suite. It does not scan a website and does not require Ollama or Chromium. The Ollama container smoke test is skipped unless explicitly enabled (see [Optional Docker test](#optional-docker-test)).

### IntelliJ IDEA

Open the cloned repository's root `pom.xml` as a Maven project, allow Maven import, and set the Project SDK to Java 21. IntelliJ `.idea` files are intentionally not committed because they contain machine-specific settings. The TestNG XML suites and all Maven configuration needed to run tests are committed.

## Scan a website with TestNG

The website scan suite uses Playwright Chromium. Install the browser once after the Maven project has been built:

```powershell
mvn -B install
mvn -pl platform-cli exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

Set the URL in `platform-cli/src/test/resources/website-analysis.xml`:

```xml
<parameter name="targetUrl" value="https://www.example.com"/>
```

Then run the separate website-analysis suite:

```powershell
mvn -B -pl platform-cli -am test '-Dtestng.suiteXmlFile=src/test/resources/website-analysis.xml'
```

You can override the XML URL without editing the file:

```powershell
mvn -B -pl platform-cli -am test '-Dtestng.suiteXmlFile=src/test/resources/website-analysis.xml' '-Daqep.url=https://www.example.com'
```

In IntelliJ, you can also run `website-analysis.xml` directly as a TestNG suite. The suite scans the site once and shows separate TestNG entries for crawling, page metadata, screenshots, accessibility, UI findings, browser diagnostics, and report generation. Findings are recorded for review; detecting a website issue does not by itself fail the scan test.

The scan report is written under `platform-cli/artifacts/`:

- `summary.html` — human-readable quality report
- `analysis.json` — structured results
- `screenshots/` — captured page screenshots
- `allure-results/` — Allure-compatible results

Open `platform-cli/artifacts/summary.html` in a browser to review the scan.

## Run the command-line scanner

After building/installing the modules and installing Chromium, run the CLI with a public URL:

```powershell
mvn -pl platform-cli exec:java "-Dexec.args=https://www.example.com --max-pages 10 --out artifacts"
```

The first argument is the target URL. Options include `--max-pages` (default 10, maximum 100), `--timeout-seconds`, `--out`, `--ollama`, `--text-model`, `--vision-model`, and `--no-ai`. For example, append `--no-ai` to scan without contacting Ollama. The output directory is relative to the process working directory; when run through the `platform-cli` Maven module, the default `artifacts` directory is `platform-cli/artifacts`.

Only public HTTP(S) URLs are accepted. Crawling is read-only and same-origin; AQEP does not click controls, submit forms, authenticate, or intentionally change site state.

## Optional local AI with Ollama

AI is not used by the default TestNG suite or by `WebsiteAnalysisTest`; that website scan explicitly disables AI so it can run without Ollama. The `OllamaContainerIntegrationTest` is a separate, optional smoke test: when enabled, it starts an Ollama Docker container and checks that `/api/tags` responds. It does **not** load a model, run inference, or verify AI analysis.

To use AI during a CLI scan:

1. Install Ollama and make sure its local server is running (normally at `http://localhost:11434`).
2. Download the models you want to use:

   ```powershell
   ollama pull qwen2.5-coder:7b
   ollama pull llava
   ollama list
   ```

3. Run the CLI command without `--no-ai`. By default AQEP uses `qwen2.5-coder:7b` for text analysis and `llava` for screenshot analysis.

Supported text models are `qwen2.5-coder:7b`, `qwen2.5-coder:14b`, and `deepseek-r1`; the approved vision model is `llava`. You can select a text model with `--text-model` and set a local Ollama endpoint with `--ollama`. Remote AI endpoints and unapproved model names are rejected. Ollama/model failures are included in the report and do not prevent the browser scan from completing.

## Optional Docker test

The Testcontainers smoke test is skipped by default. To run it, start Docker Desktop and use PowerShell:

```powershell
$env:AQEP_RUN_CONTAINER_TESTS = "true"
mvn -B verify
Remove-Item Env:AQEP_RUN_CONTAINER_TESTS
```

This downloads and starts `ollama/ollama:0.5.4` in Docker and checks its tags endpoint; it does not use a model or require a local Ollama installation.

## Docker Compose analysis

Docker Compose can run AQEP and Ollama together:

```powershell
docker compose build aqep
docker compose up -d ollama
docker compose exec ollama ollama pull qwen2.5-coder:7b
docker compose exec ollama ollama pull llava
docker compose --profile analysis run --rm aqep https://www.example.com
```

Reports are mounted in the local `artifacts/` directory, and Ollama models persist in a named volume. The browser container makes outbound requests; scan only authorized public targets and apply your organization's egress policy.

## Allure reports

AQEP writes Allure-compatible data to `platform-cli/artifacts/allure-results/` when running the TestNG website suite. If the Allure CLI is installed, generate an HTML Allure report with:

```powershell
allure generate platform-cli/artifacts/allure-results --clean -o platform-cli/artifacts/allure-report
```

## Safety and scope

AQEP rejects private, loopback, link-local, and local hostnames, blocks cross-origin navigations, and checks resource requests against private addresses. The crawler is bounded by the page limit and does not interact with site controls. A target can still serve large or hostile content, so use appropriate timeouts and network isolation.

The accessibility and UI analyzers report common heuristics such as missing labels, accessible names and image alt attributes, keyboard-focus exclusions, and broken images. Review the report findings; this is not a substitute for a full manual accessibility audit or a comprehensive broken-link scanner.

Self-healing recipes use semantic locators (role, label, placeholder, text, or test ID), not generated XPath. The Ollama agent can suggest validated recipes from a DOM snapshot. Callers compose these suggestions with `SelfHealingLocator`; successful replacements can be stored in the user's `.aqep/locator-memory.json` via `LocatorMemory.forCurrentUser()`. Avoid sharing locator memory across sites or tenants with different trust boundaries.

See [docs/architecture.md](docs/architecture.md) for module responsibilities and data flow.
