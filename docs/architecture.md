# Architecture

AQEP is a Java 21 Maven reactor with narrow modules and constructor-injected collaborators:

| Module | Responsibility |
|---|---|
| `shared` | Immutable analysis, finding, and configuration contracts |
| `core-engine` | Public-URL guardrails, same-origin bounded crawler, Playwright lifecycle, page objects |
| `ui-analysis` | Broken-image checks and semantic locator factory |
| `accessibility-engine` | Labels, accessible names, alt text, keyboard focus checks |
| `self-healing` | Semantic locator recipes and durable successful-replacement memory |
| `ai-agents` | Pluggable AI contract and local Ollama text/vision implementation |
| `reporting` | HTML dashboard, JSON result, Allure-compatible results and screenshot attachments |
| `platform-cli` | CLI composition root, analysis steps, and isolated tests |

`AnalyzedWebsitePage` is the Playwright page object. `WebsiteAnalysisSteps` composes crawler, AI, and reporter operations; TestNG test classes are kept under `platform-cli/src/test`. The crawler never clicks controls or submits forms. After capturing the initial page state, it fills visible form fields with synthetic data, blurs them, and checks browser validity and `aria-invalid` state. All network requests and navigations are blocked during this pre-submit check, and a screenshot is kept for each form. This validates client-side behavior only; it cannot exercise server-side validation. Navigation stays on the supplied origin, requests to private/local addresses are blocked, and crawl depth is capped at 100 pages. These controls are defense in depth, not a substitute for running scans in an appropriately isolated network.

The browser DOM is retained only in memory for bounded root-cause prompts and is excluded from serialized reports. AI prompts and screenshots are sent only to a validated local Ollama endpoint; remote endpoints and models outside the approved Qwen2.5-Coder, DeepSeek-R1, and LLaVA list are rejected. Disabling AI prevents model calls. Model outputs are observations, not authoritative fixes.
