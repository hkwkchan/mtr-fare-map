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
