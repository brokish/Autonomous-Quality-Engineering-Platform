package io.aqep.shared;

import java.util.Map;
import java.util.Optional;

/**
 * Metadata about scenario execution environment and configuration.
 */
public record ExecutionMetadata(
        Map<String, Object> properties
) {
    public static ExecutionMetadata empty() {
        return new ExecutionMetadata(Map.of());
    }

    public ExecutionMetadata with(String key, Object value) {
        var map = new java.util.HashMap<>(properties);
        map.put(key, value);
        return new ExecutionMetadata(Map.copyOf(map));
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(properties.get(key));
    }
}
