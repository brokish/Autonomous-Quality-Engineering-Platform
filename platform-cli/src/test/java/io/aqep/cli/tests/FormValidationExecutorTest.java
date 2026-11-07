package io.aqep.cli.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import io.aqep.core.FormValidationExecutor;
import io.aqep.core.FormValidationOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.testng.annotations.Test;

public class FormValidationExecutorTest {
    @Test
    public void fillsAndValidatesFormsWithoutSubmittingThem() throws Exception {
        Path evidence = Files.createTempDirectory("aqep-prefill-test");
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch()) {
            var page = browser.newPage();
            page.setContent("""
                    <form id="request"><label>Email <input type="email" name="email" required
                      oninput="this.form.requestSubmit()"></label><button type="submit">Send</button></form>
                    <form id="legacy"><label>Phone <input type="tel" name="phone" required
                      oninput="this.form.submit()"></label><button type="submit">Send</button></form>
                    <script>window.submits = 0; for (const f of document.forms) f.addEventListener('submit', () => window.submits++);</script>
                    """);

            FormValidationOutcome outcome = new FormValidationExecutor()
                    .validate(page, "https://example.com/contact", evidence);

            assertThat(outcome.checks()).hasSize(2).allSatisfy(check -> {
                assertThat(check.valid()).isTrue();
                assertThat(check.fieldsFilled()).isEqualTo(1);
                assertThat(check.submitted()).isFalse();
                assertThat(Files.isRegularFile(Path.of(check.screenshotPath()))).isTrue();
            });
            assertThat(outcome.findings()).isEmpty();
            assertThat(page.evaluate("window.submits")).isEqualTo(0);
        } finally {
            try (Stream<Path> files = Files.walk(evidence)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
}
