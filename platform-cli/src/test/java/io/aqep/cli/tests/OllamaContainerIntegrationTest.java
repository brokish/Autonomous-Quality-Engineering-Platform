package io.aqep.cli.tests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class OllamaContainerIntegrationTest {
    @Test
    void localOllamaContainerExposesItsHealthApi() throws Exception {
        if (!"true".equals(System.getenv("AQEP_RUN_CONTAINER_TESTS"))) {
            throw new SkipException("Set AQEP_RUN_CONTAINER_TESTS=true to run Docker integration tests");
        }
        try (GenericContainer<?> ollama = new GenericContainer<>(DockerImageName.parse("ollama/ollama:0.5.4"))
                .withExposedPorts(11434)
                .waitingFor(Wait.forHttp("/api/tags").forStatusCode(200))) {
            ollama.start();
            URI endpoint = URI.create("http://" + ollama.getHost() + ":" + ollama.getMappedPort(11434) + "/api/tags");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString());
            org.testng.Assert.assertEquals(response.statusCode(), 200);
        }
    }
}
