package com.example.fare;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FareApplicationTests {
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Autowired
	private Environment environment;

	@Autowired
	private DataService dataService;

	@Test
	void contextLoads() {
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
	void routeMinimizesTransfersBeforeStops() {
		DataService.RouteResponse route = dataService.findRoute("53", "90");

		assertEquals(9, route.stopCount());
		assertEquals(List.of("TML"), route.legs().stream().map(DataService.RouteLeg::line).toList());
		assertEquals(List.of("NAC", "AUS", "ETS", "HUH", "HOM", "TKW", "SUW", "KAT", "DIH", "HIK"),
				route.stations().stream().map(DataService.Station::stationCode).toList());
	}

	@Test
	void routeUsesFewerStopsWhenTransferCountsTie() {
		DataService.RouteResponse route = dataService.findRoute("53", "15");

		assertEquals(12, route.stopCount());
		assertEquals(List.of("TML", "KTL"), route.legs().stream().map(DataService.RouteLeg::line).toList());
		assertEquals(List.of("NAC", "AUS", "ETS", "HUH", "HOM", "TKW", "SUW", "KAT", "DIH",
				"CHH", "KOB", "NTK", "KWT"),
				route.stations().stream().map(DataService.Station::stationCode).toList());
	}

	@Test
	void routeCanStartAndEndAtTheSameStation() throws Exception {
		HttpResponse<String> response = get("/api/route?start=1&dest=1");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"stopCount\":0"));
		assertTrue(response.body().contains("\"legs\":[]"));
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
    void routeReturnsNullWhenFareIsUnavailable() throws Exception {
        HttpResponse<String> response = get("/api/route?start=44&dest=56");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"line\":\"AEL\""));
        assertTrue(response.body().contains("\"fare\":null"));
    }

	@Test
	void lineReturnsAllUniqueStationsAndDirectionSequences() throws Exception {
		HttpResponse<String> response = get("/api/lines/ael");

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"line\":\"AEL\""));
		assertTrue(response.body().contains("\"directions\":[{\"direction\":\"DT\""));
		assertTrue(response.body().contains("\"stationId\":\"56\""));
		assertEquals(15, countOccurrences(response.body(), "\"stationId\""));
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
