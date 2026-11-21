package com.example.fare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaIntegrationTests {
    @Test
    void lambdaStillServesStationSearch() throws Exception {
        String event = """
                {"resource":"/{proxy+}","path":"/api/stations","httpMethod":"GET",
                 "headers":{"Host":"example.com"},"queryStringParameters":{"query":"Tuen Mun"},
                 "multiValueQueryStringParameters":{"query":["Tuen Mun"]},
                 "requestContext":{"path":"/api/stations","httpMethod":"GET","stage":"test",
                   "identity":{"sourceIp":"127.0.0.1"}},"isBase64Encoded":false}
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new StreamLambdaHandler().handleRequest(
                new ByteArrayInputStream(event.getBytes(StandardCharsets.UTF_8)), output, null);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode response = mapper.readTree(output.toByteArray());
        assertEquals(200, response.path("statusCode").asInt(), response.toString());
        assertEquals("120", mapper.readTree(response.path("body").asText()).get(0).path("stationId").asText());
    }
}
