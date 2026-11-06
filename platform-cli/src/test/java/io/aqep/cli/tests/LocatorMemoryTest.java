package io.aqep.cli.tests;

import io.aqep.healing.LocatorMemory;
import io.aqep.healing.LocatorRecipe;
import java.nio.file.Path;
import java.nio.file.Files;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

class LocatorMemoryTest {
    @Test
    void persistsSemanticLocatorReplacementsAcrossInstances() throws Exception {
        Path tempDirectory = Files.createTempDirectory("aqep-locator-test");
        Path file = tempDirectory.resolve("locator-memory.json");
        try {
            LocatorRecipe replacement = LocatorRecipe.role("button", "Continue");
            new LocatorMemory(file).remember("checkout:continue", replacement);
            assertEquals(new LocatorMemory(file).get("checkout:continue").orElseThrow(), replacement);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDirectory);
        }
    }
}
