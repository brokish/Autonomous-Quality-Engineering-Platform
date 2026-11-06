package io.aqep.shared;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

public record AnalysisConfig(
        String targetUrl,
        Path outputDirectory,
        int maxPages,
        Duration navigationTimeout,
        String ollamaBaseUrl,
        String textModel,
        String reasoningModel,
        String visionModel) {

    private static final Set<String> TEXT_MODELS = Set.of(
            "qwen2.5-coder:7b", "qwen2.5-coder:14b", "deepseek-r1");

    public AnalysisConfig {
        if (targetUrl == null || targetUrl.isBlank()) throw new IllegalArgumentException("A target URL is required");
        if (outputDirectory == null) throw new IllegalArgumentException("An output directory is required");
        if (maxPages < 1 || maxPages > 100) throw new IllegalArgumentException("maxPages must be between 1 and 100");
        if (navigationTimeout == null || navigationTimeout.isNegative() || navigationTimeout.isZero()) {
            throw new IllegalArgumentException("navigationTimeout must be positive");
        }
        ollamaBaseUrl = ollamaBaseUrl == null || ollamaBaseUrl.isBlank() ? "http://localhost:11434" : ollamaBaseUrl;
        textModel = textModel == null || textModel.isBlank() ? "qwen2.5-coder:7b" : textModel;
        reasoningModel = reasoningModel == null || reasoningModel.isBlank() ? "deepseek-r1" : reasoningModel;
        visionModel = visionModel == null || visionModel.isBlank() ? "llava" : visionModel;
        validateLocalOllamaEndpoint(ollamaBaseUrl);
        if (!TEXT_MODELS.contains(textModel)) {
            throw new IllegalArgumentException("Unsupported text model. Allowed models: " + TEXT_MODELS);
        }
        if (!TEXT_MODELS.contains(reasoningModel)) {
            throw new IllegalArgumentException("Unsupported reasoning model. Allowed models: " + TEXT_MODELS);
        }
        if (!"llava".equals(visionModel)) {
            throw new IllegalArgumentException("Unsupported vision model. The only approved vision model is llava");
        }
    }

    /** Backward-compatible constructor for callers that configure only the text and vision model. */
    public AnalysisConfig(String targetUrl, Path outputDirectory, int maxPages, Duration navigationTimeout,
                          String ollamaBaseUrl, String textModel, String visionModel) {
        this(targetUrl, outputDirectory, maxPages, navigationTimeout, ollamaBaseUrl, textModel, "deepseek-r1", visionModel);
    }

    public static AnalysisConfig defaults(String targetUrl) {
        return new AnalysisConfig(targetUrl, Path.of("artifacts"), 10, Duration.ofSeconds(25),
                "http://localhost:11434", "qwen2.5-coder:7b", "deepseek-r1", "llava");
    }

    private static void validateLocalOllamaEndpoint(String endpoint) {
        final URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid local Ollama endpoint", exception);
        }
        String host = uri.getHost();
        if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || !Set.of("localhost", "127.0.0.1", "::1", "ollama", "host.docker.internal")
                .contains(host.replace("[", "").replace("]", "").toLowerCase())) {
            throw new IllegalArgumentException(
                    "Ollama must be an unauthenticated local HTTP service (localhost, ollama, or host.docker.internal)");
        }
    }
}
