package io.aqep.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import io.aqep.shared.Action;
import io.aqep.shared.Assertion;
import io.aqep.shared.ExecutableScenario;
import io.aqep.shared.QualityScenario;
import io.aqep.shared.StepTrace;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds multi-step end-to-end journeys against the live site. The model only chooses the next user action from
 * the elements really present on the current page; every chosen action is executed in a real browser, and the
 * assertions are taken from the state that was actually observed, so a generated test cannot reference
 * something that does not exist.
 * <p>
 * Enforces deep, comprehensive, and complex multi-step user journeys (minimum 5 steps).
 */
final class LiveJourneyExplorer implements AutoCloseable {
    /** Asks the language model and returns its raw (JSON) answer. */
    interface Model {
        String ask(String prompt) throws IOException, InterruptedException;
    }

    private record Element(String kind, String name, String href, String locator) { }

    // გაზრდილი ზღვრები უფრო გრძელი და კომპლექსური სცენარებისთვის
    private static final int MIN_STEPS = 8;
    private static final int MAX_STEPS = 12;

    private static final Pattern RISKY = Pattern.compile("(?i)\\b(buy|purchase|checkout|pay|order|subscribe|"
            + "sign ?up|sign ?in|log ?out|delete|remove|submit|donate|accept|reject|cookie|cookies|"
            + "consent|agree|privacy settings)\\b");

    private static final String OBSERVE_SCRIPT = """
            () => {
              const visible = e => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e);
                return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
              const label = e => (e.getAttribute('aria-label') || e.innerText || e.getAttribute('title')
                || e.getAttribute('placeholder') || '').replace(/\\s+/g, ' ').trim();
              const out = [];
              document.querySelectorAll('a[href], button, [role=button], [role=searchbox], input').forEach(e => {
                if (!visible(e)) return;
                const tag = e.tagName.toLowerCase();
                const type = (e.getAttribute('type') || 'text').toLowerCase();
                if (tag === 'input' && !['text', 'search'].includes(type)) return;
                const name = label(e);
                if (!name) return;
                const input = tag === 'input' || e.getAttribute('role') === 'searchbox';
                out.push({kind: input ? 'INPUT' : (tag === 'a' ? 'LINK' : 'BUTTON'), name: name.slice(0, 80),
                  href: tag === 'a' ? e.href : '', id: e.id || '', search: type === 'search'
                    || e.getAttribute('role') === 'searchbox'});
              });
              return out;
            }
            """;

    private static final String HEADING_SCRIPT = """
            () => { const h = document.querySelector('h1');
              return h ? h.innerText.replace(/\\s+/g, ' ').trim() : ''; }
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Playwright playwright = Playwright.create();
    private final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

    Optional<ExecutableScenario> explore(QualityScenario scenario, String startUrl, Model model)
            throws IOException, InterruptedException {
        URI start = URI.create(startUrl);
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1440, 900).setLocale("en-US"))) {
            Page page = context.newPage();
            page.setDefaultTimeout(8_000);
            page.navigate(startUrl);
            settle(page);
            List<Action> actions = new ArrayList<>();
            List<StepTrace> trace = new ArrayList<>();
            List<String> history = new ArrayList<>();
            Set<String> used = new HashSet<>();
            int declined = 0;

            while (actions.size() < MAX_STEPS && declined < 3) {
                List<Element> elements = observe(page).stream()
                        .filter(element -> !used.contains(element.kind() + "|" + element.name())).toList();
                if (elements.isEmpty()) break;

                JsonNode choice = choose(model, scenario, page, elements, history, actions.size());
                String type = choice.path("action").asText("DONE").toUpperCase();
                int index = choice.path("element").asInt(-1);

                if (type.equals("DONE") || index < 0 || index >= elements.size()) {
                    if (actions.size() >= MIN_STEPS) break;
                    declined++;
                    history.add("(You must take AT LEAST " + MIN_STEPS + " deep steps before finishing. Explore further!)");
                    continue;
                }

                Element element = elements.get(index);
                boolean search = type.equals("SEARCH") && element.kind().equals("INPUT");
                boolean click = type.equals("CLICK") && !element.kind().equals("INPUT");

                if (!search && !click) {
                    declined++;
                    history.add("(Invalid action: " + type + " on " + element.kind() + " \"" + element.name() + "\".)");
                    continue;
                }

                String before = page.url();
                String term = cleanTerm(choice.path("term").asText(""));
                try {
                    Locator target = page.locator(element.locator());
                    if (search) {
                        target.fill(term);
                        target.press("Enter");
                    } else {
                        target.click();
                    }
                    settle(page);
                } catch (RuntimeException exception) {
                    history.add("(Failed: " + type + " \"" + element.name() + "\" did not work.)");
                    break;
                }

                used.add(element.kind() + "|" + element.name());
                String after = page.url();
                if (!sameSite(start, after)) return Optional.empty();

                boolean moved = !pathOf(before).equals(pathOf(after));
                actions.add(search ? new Action.SearchAction(element.locator(), term)
                        : new Action.ClickAction(element.locator()));
                trace.add(new StepTrace(before, after, moved ? urlPart(after) : null,
                        moved ? titleKey(page.title()) : null));
                history.add((search ? "SEARCH \"" + term + "\" in " : "CLICK ") + "\"" + element.name() + "\" -> "
                        + after + " (" + abbreviate(page.title(), 60) + ")");
            }

            if (actions.size() < MIN_STEPS || trace.stream().noneMatch(step -> step.expectUrlPart() != null)) {
                return Optional.empty();
            }

            List<Assertion> assertions = finalAssertions(page, start);
            if (assertions.isEmpty()) return Optional.empty();

            return Optional.of(new ExecutableScenario(scenario.title(), startUrl, List.of(), actions, assertions, true,
                    Optional.of(scenario.category() + ": " + scenario.rationale()), trace));
        }
    }

    private JsonNode choose(Model model, QualityScenario scenario, Page page, List<Element> elements,
                            List<String> history, int done) throws IOException, InterruptedException {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            list.append(i).append(". ").append(elements.get(i).kind()).append(' ')
                    .append(elements.get(i).name()).append('\n');
        }

        // მკაცრი და გაძლიერებული პრომპტი მოდელისთვის, რომ მოითხოვოს კომპლექსური და გრძელი სცენარები
        String prompt = """
                You are a senior QA automation engineer recording a DEEP, COMPLEX, and COMPREHENSIVE end-to-end test on a live website.
                Test goal: %s
                Why it matters: %s

                Steps done so far:
                %s
                Current page: %s (%s)
                Interactive elements on this page (index. KIND name):
                %s

                MANDATORY INSTRUCTIONS FOR COMPLEXITY:
                - This journey MUST be deep and thorough. You MUST perform BETWEEN %d AND %d steps.
                - DO NOT stop early! Since you have only completed %d steps so far, you MUST continue exploring deeper navigation, menus, filters, or sub-pages relevant to the goal.
                - Only use DONE when the complete complex flow is fully satisfied and you have reached at least %d steps.
                - Avoid superficial short paths; act like a real user performing an extensive workflow.
                
                SEARCH only works on INPUT elements, CLICK only on LINK or BUTTON. Respond strictly as JSON:
                {"action":"CLICK|SEARCH|DONE","element":0,"term":"short search term for SEARCH","why":"..."}
                """.formatted(scenario.title(), scenario.rationale(),
                history.isEmpty() ? "(none)" : String.join("\n", history), page.url(), abbreviate(page.title(), 60),
                list, MIN_STEPS, MAX_STEPS, done, MIN_STEPS);

        String response = model.ask(prompt);
        int start = response.indexOf('{'), end = response.lastIndexOf('}');
        if (start < 0 || end <= start) return mapper.createObjectNode().put("action", "DONE");
        return mapper.readTree(response.substring(start, end + 1));
    }

    private List<Element> observe(Page page) {
        Object raw = page.evaluate(OBSERVE_SCRIPT);
        Map<String, Element> unique = new LinkedHashMap<>();
        if (!(raw instanceof List<?> items)) return List.of();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String kind = String.valueOf(map.get("kind"));
            String name = String.valueOf(map.get("name"));
            if (name.contains("\"") || name.contains("\\") || RISKY.matcher(name).find()) continue;
            String locator = switch (kind) {
                case "LINK" -> "role=link[name=\"" + name + "\"] >> nth=0";
                case "BUTTON" -> "role=button[name=\"" + name + "\"] >> nth=0";
                default -> Boolean.TRUE.equals(map.get("search")) ? "role=searchbox >> nth=0"
                        : String.valueOf(map.get("id")).isBlank() ? "role=textbox[name=\"" + name + "\"] >> nth=0"
                        : "css=#" + map.get("id") + " >> nth=0";
            };
            unique.putIfAbsent(kind + "|" + name, new Element(kind, name, String.valueOf(map.get("href")), locator));
            if (unique.size() >= 40) break;
        }
        return List.copyOf(unique.values());
    }

    /** Assertions describe the page the journey ended on, taken from what the browser really shows. */
    private List<Assertion> finalAssertions(Page page, URI start) {
        List<Assertion> assertions = new ArrayList<>();
        String url = urlPart(page.url());
        if (url != null) assertions.add(new Assertion.UrlContainsAssertion(url));
        String title = titleKey(page.title());
        if (title != null) assertions.add(new Assertion.PageTitleAssertion(title, true));
        Object heading = page.evaluate(HEADING_SCRIPT);
        String text = heading == null ? "" : heading.toString();
        if (text.length() >= 3 && text.length() <= 80 && !text.contains("\"") && !text.contains("\\")) {
            assertions.add(new Assertion.ElementVisibleAssertion("role=heading[name=\"" + text + "\"] >> nth=0",
                    Optional.empty()));
        }
        return assertions;
    }

    private static void settle(Page page) {
        try {
            page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(10_000));
        } catch (RuntimeException ignored) {
            // Slow pages are judged by the assertions, not by the wait.
        }
        page.waitForTimeout(800);
    }

    private static boolean sameSite(URI start, String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && host.equalsIgnoreCase(start.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    /** The path when it is distinctive, otherwise the host. */
    private static String urlPart(String url) {
        String path = pathOf(url);
        if (path.length() >= 2) return path;
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** First segment of a title ("Page | Site" -> "Page"). */
    private static String titleKey(String title) {
        String key = title == null ? "" : title.split("\\s[|\\u2013\\u2014-]\\s")[0].strip();
        return key.length() >= 3 && key.length() <= 60 ? key : null;
    }

    private static String cleanTerm(String term) {
        String clean = term.replaceAll("[^\\p{L}\\p{N} ]", "").strip();
        return clean.isBlank() || clean.length() > 40 ? "test" : clean;
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
    }
}