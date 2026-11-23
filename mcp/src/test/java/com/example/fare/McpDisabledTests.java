package com.example.fare;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpDisabledTests {
    @Autowired
    private Environment environment;

    @Test
    void mcpIsDisabledWithoutTheProfile() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp")).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }
}
