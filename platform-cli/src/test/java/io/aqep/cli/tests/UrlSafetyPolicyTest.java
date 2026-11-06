package io.aqep.cli.tests;

import io.aqep.core.UrlSafetyPolicy;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

class UrlSafetyPolicyTest {
    private final UrlSafetyPolicy policy = new UrlSafetyPolicy();

    @Test
    void acceptsPublicHttpUrlAndNormalizesOriginComparison() {
        var uri = policy.validatePublicUrl("https://93.184.216.34/path");
        assertEquals(uri.getScheme(), "https");
        assertTrue(policy.sameOrigin(uri, policy.validatePublicUrl("https://93.184.216.34:443/other")));
    }

    @Test
    void rejectsLoopbackTargets() {
        expectThrows(IllegalArgumentException.class, () -> policy.validatePublicUrl("http://127.0.0.1"));
    }

    @Test
    void rejectsNonHttpSchemesAndCredentialedTargets() {
        expectThrows(IllegalArgumentException.class, () -> policy.validatePublicUrl("file:///etc/passwd"));
        expectThrows(IllegalArgumentException.class, () -> policy.validatePublicUrl("https://user:pass@example.com"));
    }
}
