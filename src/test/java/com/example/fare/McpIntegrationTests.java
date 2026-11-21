package com.example.fare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mcp")
class McpIntegrationTests {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private Environment environment;

    @Test
    void clientCanDiscoverAndCallTools() throws Exception {
        HttpResponse<String> initialized = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26","capabilities":{},
                  "clientInfo":{"name":"integration-test","version":"1.0"}}}
                """);
        assertEquals(200, initialized.statusCode(), initialized.body());
        assertNotNull(response(initialized).path("result").get("serverInfo"));
        String session = initialized.headers().firstValue("mcp-session-id").orElseThrow();

        assertEquals(202, post(session, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """).statusCode());
        JsonNode tools = response(post(session, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """)).path("result").path("tools");
        assertEquals(2, tools.size());
        JsonNode searchTool = findTool(tools, "search_stations");
        JsonNode routeTool = findTool(tools, "get_route");
        assertTrue(searchTool.path("inputSchema").path("properties").has("query"));
        assertTrue(routeTool.path("inputSchema").path("properties").has("start"));
        assertTrue(routeTool.path("inputSchema").path("properties").has("dest"));

        JsonNode start = call(session, 3, "search_stations", "{\"query\":\"Tuen Mun\"}");
        assertFalse(start.path("isError").asBoolean());
        assertEquals("120", toolContent(start).get(0).path("stationId").asText());
        JsonNode dest = call(session, 4, "search_stations", "{\"query\":\"Kwun Tong\"}");
        assertFalse(dest.path("isError").asBoolean());
        assertEquals("15", toolContent(dest).get(0).path("stationId").asText());
        JsonNode route = call(session, 5, "get_route", "{\"start\":\"120\",\"dest\":\"15\"}");
        assertFalse(route.path("isError").asBoolean(), route.toString());
        JsonNode journey = toolContent(route);
        assertEquals("120", journey.path("startStationId").asText());
        assertEquals("15", journey.path("destinationStationId").asText());
        assertTrue(journey.path("stopCount").asInt() > 0);
        assertEquals("Tuen Mun", journey.path("stations").get(0).path("englishName").asText());
        assertEquals("Kwun Tong", journey.path("stations").get(journey.path("stations").size() - 1)
                .path("englishName").asText());
        assertEquals(2490, journey.path("fare").path("octAdultCents").asInt());

        JsonNode missing = call(session, 6, "search_stations", "{\"query\":\"not-a-station\"}");
        assertFalse(missing.path("isError").asBoolean());
        assertTrue(toolContent(missing).isEmpty());
        JsonNode invalid = call(session, 7, "get_route", "{\"start\":\"999\",\"dest\":\"15\"}");
        assertTrue(invalid.path("isError").asBoolean(), invalid.toString());
        JsonNode blank = call(session, 8, "search_stations", "{\"query\":\" \"}");
        assertTrue(blank.path("isError").asBoolean(), blank.toString());

        HttpResponse<String> rest = CLIENT.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + environment.getProperty("local.server.port")
                        + "/api/stations?query=Tuen%20Mun"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rest.statusCode());
        assertEquals("120", JSON.readTree(rest.body()).get(0).path("stationId").asText());
    }

    private JsonNode findTool(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("Missing MCP tool: " + name);
    }

    private JsonNode toolContent(JsonNode result) throws Exception {
        return JSON.readTree(result.path("content").get(0).path("text").asText());
    }

    private JsonNode call(String session, int id, String name, String arguments) throws Exception {
        HttpResponse<String> result = post(session, """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"%s","arguments":%s}}
                """.formatted(id, name, arguments));
        assertEquals(200, result.statusCode(), result.body());
        JsonNode rpc = response(result);
        assertFalse(rpc.has("error"), rpc.toString());
        return rpc.path("result");
    }

    private HttpResponse<String> post(String session, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode response(HttpResponse<String> response) throws Exception {
        String body = response.body();
        if (response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream")) {
            body = body.lines().filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim()).findFirst().orElseThrow();
        }
        return JSON.readTree(body);
    }
}
