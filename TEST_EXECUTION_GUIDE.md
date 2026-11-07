# 🚀 AQEP Test Execution Guide

## Test Suites

### 1️⃣ **Full Flow with Ollama AI** (Intelligent Testing)
**File:** `platform-cli/src/test/resources/suites/testng-full-flow-with-ollama.xml`

**What it does:**
- ✅ End-to-end website quality analysis
- ✅ AI-powered test scenario generation (Ollama)
- ✅ Visual intelligence with LLaVA
- ✅ Root cause analysis with DeepSeek-R1
- ✅ Self-healing locator recovery
- ✅ Quality scoring & AI summary

**Best for:**
- Comprehensive quality analysis
- Production deployment checks
- Deep insights into website quality

**Execution:**
```bash
# Maven
mvn -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/suites/testng-full-flow-with-ollama.xml -Daqep.url=https://example.com

# IntelliJ
Right-click platform-cli/src/test/resources/suites/testng-full-flow-with-ollama.xml → Run
```

**Duration:** 4-6 minutes (includes Ollama inference)
**Requires:** Ollama running locally (port 11434)

---

### 2️⃣ **Fast Flow without Ollama** (CI/CD Pipeline)
**File:** `platform-cli/src/test/resources/suites/testng-fast-without-ollama.xml`

**What it does:**
- ✅ Deterministic validation only
- ✅ Core scenario execution
- ✅ Element verification
- ✅ Error detection & reporting
- ❌ No AI analysis (fast!)

**Best for:**
- Continuous Integration pipelines
- Quick feedback in development
- Early smoke testing

**Execution:**
```bash
# Maven
mvn -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/suites/testng-fast-without-ollama.xml -Daqep.url=https://example.com

# IntelliJ
Right-click platform-cli/src/test/resources/suites/testng-fast-without-ollama.xml → Run
```

**Duration:** 2-3 minutes (parallel execution)
**Requires:** None (just browser)

---

## Running Tests

### Option 1: Maven Command Line
```bash
# AI-powered full analysis
mvn -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/suites/testng-full-flow-with-ollama.xml -Daqep.url=https://example.com

# Fast deterministic testing
mvn -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/suites/testng-fast-without-ollama.xml -Daqep.url=https://example.com
```

### Option 2: IntelliJ IDEA
1. Right-click XML file in project tree
2. Select **Run**
3. View results in TestNG console

### Option 3: GitHub Actions (CI/CD)
```yaml
- name: Run AQEP Tests (Fast)
  run: mvn -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/suites/testng-fast-without-ollama.xml -Daqep.url=${{ github.event.inputs.url }}

- name: Run AQEP Tests (Full AI Analysis)
  run: mvn -pl platform-cli -am test -Dtestng.suiteXmlFile=src/test/resources/suites/testng-full-flow-with-ollama.xml -Daqep.url=${{ github.event.inputs.url }}
```

---

## Test Phases

### Fast Suite (platform-cli/src/test/resources/suites/testng-fast-without-ollama.xml)
1. **Core Scenario Execution** - Navigation & action flow
2. **Action Validation** - Individual action tests
3. **Assertion Validation** - Element verification
4. **Locator Resolution** - Selector parsing
5. **Page Context** - Screenshot & log capture
6. **Error Handling** - Failure recovery

### AI Suite (platform-cli/src/test/resources/suites/testng-full-flow-with-ollama.xml)
1. **Phase 1** - Discovery & Analysis
2. **Phase 2** - Ollama AI Analysis
3. **Phase 3** - Self-Healing
4. **Phase 4** - Root Cause Analysis
5. **Phase 5** - Quality Scoring
6. **Phase 6** - Report Generation

---

## Configuration Parameters

### Common Parameters
| Parameter | Default | Description |
|-----------|---------|-------------|
| `targetUrl` | https://example.com | Website to analyze |
| `screenshotOnFailure` | true | Capture screenshot when test fails |

### AI Suite Parameters
| Parameter | Default | Description |
|-----------|---------|-------------|
| `ollamaEnabled` | true | Enable Ollama integration |
| `ollamaTimeout` | 30000 | Ollama inference timeout (ms) |
| `enableVisualAnalysis` | true | Run LLaVA visual analysis |
| `enableRootCauseAnalysis` | true | Use DeepSeek-R1 for root cause |

### Fast Suite Parameters
| Parameter | Default | Description |
|-----------|---------|-------------|
| `ollamaEnabled` | false | Disable Ollama (faster) |
| `enableVisualAnalysis` | false | Skip visual analysis |
| `enableRootCauseAnalysis` | false | Skip root cause analysis |
| `pageLoadTimeout` | 10000 | Page load timeout (ms) |
| `actionTimeout` | 5000 | Action timeout (ms) |

---

## Reports

### TestNG HTML Report
```
core-engine/test-output/index.html
```

### Allure Report
```bash
allure serve allure-results/
```

### Ollama Analysis Report
Generated in HTML after full flow execution

---

## Performance Optimization

### For Faster Tests
- Use **platform-cli/src/test/resources/suites/testng-fast-without-ollama.xml**
- Parallel execution enabled (4 threads)
- No Ollama inference overhead
- Expected: 2-3 minutes

### For Better Insights
- Use **platform-cli/src/test/resources/suites/testng-full-flow-with-ollama.xml**
- Sequential execution (avoid rate limiting)
- Ollama inference: 10-30 seconds per phase
- Expected: 4-6 minutes

### Ollama Performance
- Model loading: ~5 seconds (first run)
- Inference per analysis: ~10-30 seconds
- Inference timeout: 30 seconds
- To speed up: Use smaller models (phi, orca-mini)

---

## Prerequisites

### For Fast Suite
- Java 21+
- Maven
- Chrome/Chromium browser
- Playwright binaries (auto-downloaded)

### For AI Suite
- All fast suite prerequisites
- Ollama running: `ollama serve`
- Models downloaded:
  - `qwen2.5-coder` (test generation)
  - `deepseek-r1` (root cause analysis)
  - `llava` (visual analysis)

---

## Setup Ollama

```bash
# Start Ollama server
ollama serve

# In another terminal, pull models
ollama pull qwen2.5-coder
ollama pull deepseek-r1
ollama pull llava

# Verify endpoint
curl http://localhost:11434/api/tags
```

---

## Generated tests (Page Object Model)

After the AI scenarios run, every **passing** scenario is saved permanently under
`platform-cli/src/test/java/io/aqep/generated/<site>/{pages,steps,tests}` (e.g. `jetbrains`, `amazon`).
Scenarios accumulate in `src/test/resources/generated/<site>/scenarios.json`; earlier ones are never deleted,
and sources are rebuilt from that registry (do not hand-edit generated files).

Run them separately from the AI cycle (no Ollama needed, deterministic) as a regression suite:

```
mvn -pl platform-cli -am test "-Dtestng.suiteXmlFile=src/test/resources/suites/testng-regression.xml"
```

Commit `src/test/java/io/aqep/generated/**` and `src/test/resources/generated/**` so the tests stay in git.
All generated tests carry the `regression` group plus a per-site group (`-Dgroups=jetbrains`). Per-site/all suites:

```
mvn -pl platform-cli -am test "-Dtestng.suiteXmlFile=src/test/resources/suites/generated/testng-generated-<site>.xml"
mvn -pl platform-cli -am test "-Dtestng.suiteXmlFile=src/test/resources/suites/generated/testng-generated-all.xml"
```

or run any generated test class/method from the IDE. Options: `-Daqep.headless=false`,
`-Daqep.generated.baseUrl=https://staging.example.com` (replaces the recorded origin).

## Troubleshooting

### Test hangs on Ollama
- Check Ollama is running: `curl http://localhost:11434`
- Increase `ollamaTimeout` parameter
- Use smaller models (faster)

### Tests fail with "Element not found"
- Fast suite: Increase `actionTimeout`
- AI suite: Enable self-healing (automatic retry)
- Check screenshot in test output

### Reports not generated
```bash
mvn clean test -DfailIfNoTests=false
mvn allure:report
```

---

## Next Steps

- ✅ Run fast suite to verify setup
- ✅ Check test output & screenshots
- ✅ Run AI suite to see Ollama in action
- ✅ Review generated reports
- ✅ Integrate into CI/CD pipeline

**Happy testing!** 🎉

