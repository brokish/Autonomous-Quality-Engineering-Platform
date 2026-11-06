package io.aqep.healing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LocatorMemory {
    private static final TypeReference<Map<String, LocatorRecipe>> MEMORY_TYPE = new TypeReference<>() {};
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, LocatorRecipe> entries;

    public LocatorMemory(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            entries = new HashMap<>(mapper.readValue(file.toFile(), MEMORY_TYPE));
        } else {
            entries = new HashMap<>();
        }
    }

    public static LocatorMemory forCurrentUser() throws IOException {
        return new LocatorMemory(Path.of(System.getProperty("user.home"), ".aqep", "locator-memory.json"));
    }

    public synchronized Optional<LocatorRecipe> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public synchronized void remember(String key, LocatorRecipe recipe) throws IOException {
        entries.put(key, recipe);
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "locator-memory-", ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), entries);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
