package com.example.fare;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FareApplicationTests {
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private Environment environment;

	@Autowired
	private DataService dataService;

	@Test
	void contextLoads() {
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void logsDataServiceInitializationTime(CapturedOutput output) {
		new DataService();
		assertTrue(output.getOut().matches("(?s).*DataService initialized in \\d+ ms.*"));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void logsApiRequestsWithoutQueryParameters(CapturedOutput output) throws Exception {
		assertEquals(200, get("/api/route?start=1&dest=3").statusCode());
		assertEquals(400, get("/api/stations?query=").statusCode());
		assertEquals(200, get("/").statusCode());

		String logs = output.getOut();
		assertTrue(logs.matches("(?s).*API GET /api/route status=200 duration=\\d+ ms.*"));
		assertTrue(logs.matches("(?s).*API GET /api/stations status=400 duration=\\d+ ms.*"));
		assertFalse(logs.contains("start=1"));
		assertFalse(logs.contains("query="));
		assertEquals(2, countOccurrences(logs, "API GET "));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void logsUnhandledApiFailures(CapturedOutput output) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/route");
		MockHttpServletResponse response = new MockHttpServletResponse();
		ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

		assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
			throw new ServletException("test failure");
		}));
		assertTrue(output.getOut().matches("(?s).*API GET /api/route status=500 duration=\\d+ ms.*"));
	}

    @Test
    void mcpIsDisabledByDefault() throws Exception {
        assertEquals(404, get("/mcp").statusCode());
    }

    @Test
    void stationSearchSupportsNamesAndCodes() throws Exception {
        HttpResponse<String> byName = get("/api/stations?query=tuen%20mu");
        assertEquals(200, byName.statusCode());
        assertTrue(byName.body().contains("\"stationId\":\"120\""));
        assertTrue(byName.body().contains("\"stationCode\":\"TUM\""));
        assertTrue(byName.body().contains("\"englishName\":\"Tuen Mun\""));
        assertEquals(1, countOccurrences(byName.body(), "\"stationId\":\"120\""));

        HttpResponse<String> byCode = get("/api/stations?query=tum");
        assertEquals(200, byCode.statusCode());
        assertTrue(byCode.body().contains("\"stationId\":\"120\""));

        HttpResponse<String> byChineseName = get("/api/stations?query=%E5%B1%AF%E9%96%80");
        assertEquals(200, byChineseName.statusCode());
        assertTrue(byChineseName.body().contains("\"stationId\":\"120\""));

        HttpResponse<String> noMatches = get("/api/stations?query=not-a-station");
        assertEquals(200, noMatches.statusCode());
        assertEquals("[]", noMatches.body());
    }

    @Test
    void stationSearchRejectsMissingOrBlankQueries() throws Exception {
        assertEquals(400, get("/api/stations").statusCode());
        assertEquals(400, get("/api/stations?query=").statusCode());
        assertEquals(400, get("/api/stations?query=%20%20").statusCode());
    }

    @Test
    void allStationsEndpointReturnsEveryStation() throws Exception {
        HttpResponse<String> response = get("/api/stations/all");

        assertEquals(200, response.statusCode());
        assertEquals(95, countOccurrences(response.body(), "\"stationId\""));
        assertTrue(response.body().contains("\"stationId\":\"120\""));
        assertTrue(response.body().contains("\"stationCode\":\"TUM\""));
        assertFalse(response.body().contains("\"stationId\":\"47\""));
        assertFalse(response.body().contains("\"stationId\":\"56\""));
    }

	@Test
	void routePrefersStayingOnOneLine() throws Exception {
		HttpResponse<String> response = get("/api/route?start=1&dest=3");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"startStationId\":\"1\""));
		assertTrue(response.body().contains("\"destinationStationId\":\"3\""));
		assertTrue(response.body().contains("\"stopCount\":2"));
		assertTrue(response.body().contains("\"line\":\"TWL\""));
		assertEquals(1, countOccurrences(response.body(), "\"line\":"));
	}

	@Test
	void routeReturnsDirectionsForBothWays() throws Exception {
		HttpResponse<String> outbound = get("/api/route?start=120&dest=119");
		HttpResponse<String> inbound = get("/api/route?start=119&dest=120");

		assertEquals(200, outbound.statusCode());
		assertTrue(outbound.body().contains("\"line\":\"TML\",\"direction\":\"DT\""));
		assertEquals(200, inbound.statusCode());
		assertTrue(inbound.body().contains("\"line\":\"TML\",\"direction\":\"UT\""));
	}

	@Test
	void routeResponseContainsStationsOnlyWithinLegs() throws Exception {
		HttpResponse<String> response = get("/api/route?start=114&dest=15");
		assertEquals(200, response.statusCode());
		JsonNode route = JSON.readTree(response.body());
		assertFalse(route.has("stations"));
		assertEquals(14, route.path("stopCount").asInt());
		JsonNode legs = route.path("legs");
		assertEquals(2, legs.size());
		JsonNode firstStations = legs.get(0).path("stations");
		JsonNode lastStations = legs.get(1).path("stations");
		assertEquals("114", firstStations.get(0).path("stationId").asText());
		assertEquals("11", firstStations.get(firstStations.size() - 1).path("stationId").asText());
		assertEquals("11", lastStations.get(0).path("stationId").asText());
		assertEquals("15", lastStations.get(lastStations.size() - 1).path("stationId").asText());
		assertTrue(route.has("fare"));
	}

	@Test
	void branchDirectionsUseUtOrDt() {
		DataService.RouteResponse route = dataService.findRoute("49", "57");

		assertEquals(List.of("TKL"), route.legs().stream().map(DataService.RouteLeg::line).toList());
		assertEquals(List.of("UT"), route.legs().stream().map(DataService.RouteLeg::direction).toList());
		assertEquals(List.of("TIK", "TKO", "LHP"),
				route.legs().get(0).stations().stream().map(DataService.Station::stationCode).toList());
	}

	@Test
	void routeMinimizesTransfersBeforeStops() {
		DataService.RouteResponse route = dataService.findRoute("53", "90");

		assertEquals(9, route.stopCount());
		assertEquals(List.of("TML"), route.legs().stream().map(DataService.RouteLeg::line).toList());
		assertEquals(List.of("NAC", "AUS", "ETS", "HUH", "HOM", "TKW", "SUW", "KAT", "DIH", "HIK"),
				route.legs().get(0).stations().stream().map(DataService.Station::stationCode).toList());
	}

	@Test
	void routeUsesFewerStopsWhenTransferCountsTie() {
		DataService.RouteResponse route = dataService.findRoute("53", "15");

		assertEquals(12, route.stopCount());
		assertEquals(List.of("TML", "KTL"), route.legs().stream().map(DataService.RouteLeg::line).toList());
		assertEquals(List.of("DT", "UT"), route.legs().stream().map(DataService.RouteLeg::direction).toList());
		assertEquals(List.of("NAC", "AUS", "ETS", "HUH", "HOM", "TKW", "SUW", "KAT", "DIH"),
				route.legs().get(0).stations().stream().map(DataService.Station::stationCode).toList());
		assertEquals(List.of("DIH", "CHH", "KOB", "NTK", "KWT"),
				route.legs().get(1).stations().stream().map(DataService.Station::stationCode).toList());
	}

	@Test
	void routeCanStartAndEndAtTheSameStation() throws Exception {
		HttpResponse<String> response = get("/api/route?start=1&dest=1");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"stopCount\":0"));
		assertTrue(response.body().contains("\"legs\":[]"));
		assertFalse(JSON.readTree(response.body()).has("stations"));
        assertTrue(response.body().contains("\"octAdultCents\":0"));
	}

    @Test
    void routeIncludesAllFareCategories() throws Exception {
        HttpResponse<String> response = get("/api/route?start=1&dest=3");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"fare\":{\"startStationId\":\"1\",\"destinationStationId\":\"3\","
                + "\"octAdultCents\":1060,\"octStudentCents\":540,\"octJoyyouSixtyCents\":210,"
                + "\"singleAdultCents\":1250,\"octChildCents\":540,\"octElderlyCents\":210,"
                + "\"octPwdCents\":210,\"singleChildCents\":550,\"singleElderlyCents\":550}"));
    }

    @Test
    void airportExpressStationsAreUnavailableWithoutFareData() throws Exception {
        assertEquals(404, get("/api/route?start=44&dest=56").statusCode());
    }

	@Test
	void airportExpressLineIsUnavailableWithoutFareData() throws Exception {
		assertEquals(404, get("/api/lines/ael").statusCode());
	}

	@Test
	void unknownStationReturnsNotFound() throws Exception {
		assertEquals(404, get("/api/route?start=999&dest=1").statusCode());
	}

	@Test
	void disconnectedStationsReturnNotFound() throws Exception {
		assertEquals(404, get("/api/route?start=1&dest=47").statusCode());
	}

	@Test
	void unknownLineReturnsNotFound() throws Exception {
		assertEquals(404, get("/api/lines/UNKNOWN").statusCode());
	}

	private HttpResponse<String> get(String path) throws Exception {
		int port = environment.getRequiredProperty("local.server.port", Integer.class);
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.GET()
				.build();
		return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private int countOccurrences(String value, String target) {
		return value.split(target, -1).length - 1;
	}
}
