package com.example.fare;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DataService {
	private static final String STATIONS_RESOURCE = "data/mtr_lines_and_stations.csv";
    private static final String FARES_RESOURCE = "data/mtr_lines_fares.csv";

	private final Map<String, Station> stations;
	private final Map<String, LineResponse> lines;
	private final Map<String, List<Edge>> connections;
    private final FareTable fareTable;

	public DataService() {
		LoadedData data = loadData();
		stations = data.stations();
		lines = data.lines();
		connections = data.connections();
        fareTable = FareTable.load(new ClassPathResource(FARES_RESOURCE));
	}

    public List<Station> searchStations(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not be blank");
        }

        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return stations.values().stream()
                .filter(station -> station.englishName().toLowerCase(Locale.ROOT).contains(normalized)
                        || station.chineseName().toLowerCase(Locale.ROOT).contains(normalized)
                        || station.stationCode().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

	public LineResponse findLine(String lineCode) {
		String normalizedCode = lineCode.trim().toUpperCase(Locale.ROOT);
		LineResponse line = lines.get(normalizedCode);
		if (line == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown line: " + lineCode);
		}
		return line;
	}

	public RouteResponse findRoute(String startStationId, String destinationStationId) {
		validateStationId(startStationId);
		validateStationId(destinationStationId);

		if (!stations.containsKey(startStationId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown station ID: " + startStationId);
		}
		if (!stations.containsKey(destinationStationId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown station ID: " + destinationStationId);
		}

		RouteSearchNode destination = findRouteWithFewestTransfers(startStationId, destinationStationId);
		if (destination == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"No route found between station IDs " + startStationId + " and " + destinationStationId);
		}

		List<String> stationIds = new ArrayList<>();
		List<String> linesByHop = new ArrayList<>();
		RouteSearchNode current = destination;
		stationIds.add(current.state().stationId());
		while (current.previous() != null) {
			linesByHop.add(current.state().lineCode());
			current = current.previous();
			stationIds.add(current.state().stationId());
		}
		java.util.Collections.reverse(stationIds);
		java.util.Collections.reverse(linesByHop);

		List<Station> routeStations = stationIds.stream().map(stations::get).toList();
		return new RouteResponse(
				startStationId,
				destinationStationId,
				routeStations.size() - 1,
				routeStations,
				buildLegs(stationIds, linesByHop),
                fareTable.find(startStationId, destinationStationId));
	}

	private RouteSearchNode findRouteWithFewestTransfers(String startStationId, String destinationStationId) {
		Comparator<RouteSearchNode> byCost = Comparator.comparingInt(RouteSearchNode::transfers)
				.thenComparingInt(RouteSearchNode::stops);
		PriorityQueue<RouteSearchNode> queue = new PriorityQueue<>(
				byCost.thenComparingLong(RouteSearchNode::order));
		Map<RouteState, RouteSearchNode> bestRoutes = new HashMap<>();
		long order = 0;
		RouteSearchNode start = new RouteSearchNode(new RouteState(startStationId, null), 0, 0, order++, null);
		queue.add(start);
		bestRoutes.put(start.state(), start);

		while (!queue.isEmpty()) {
			RouteSearchNode current = queue.remove();
			if (bestRoutes.get(current.state()) != current) {
				continue;
			}
			if (current.state().stationId().equals(destinationStationId)) {
				return current;
			}
			for (Edge edge : connections.getOrDefault(current.state().stationId(), List.of())) {
				String currentLine = current.state().lineCode();
				int transfers = current.transfers()
						+ (currentLine != null && !currentLine.equals(edge.lineCode()) ? 1 : 0);
				RouteSearchNode next = new RouteSearchNode(
						new RouteState(edge.stationId(), edge.lineCode()),
						transfers, current.stops() + 1, order++, current);
				RouteSearchNode best = bestRoutes.get(next.state());
				if (best == null || byCost.compare(next, best) < 0) {
					bestRoutes.put(next.state(), next);
					queue.add(next);
				}
			}
		}
		return null;
	}

	private List<RouteLeg> buildLegs(List<String> stationIds, List<String> linesByHop) {
		if (linesByHop.isEmpty()) {
			return List.of();
		}

		List<RouteLeg> legs = new ArrayList<>();
		String currentLine = linesByHop.get(0);
		List<Station> legStations = new ArrayList<>();
		legStations.add(stations.get(stationIds.get(0)));
		for (int hop = 0; hop < linesByHop.size(); hop++) {
			legStations.add(stations.get(stationIds.get(hop + 1)));
			if (hop + 1 < linesByHop.size() && !currentLine.equals(linesByHop.get(hop + 1))) {
				legs.add(new RouteLeg(currentLine, legStations));
				currentLine = linesByHop.get(hop + 1);
				legStations = new ArrayList<>();
				legStations.add(stations.get(stationIds.get(hop + 1)));
			}
		}
		legs.add(new RouteLeg(currentLine, legStations));
		return List.copyOf(legs);
	}

	private void validateStationId(String stationId) {
		if (stationId == null || stationId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Station IDs must not be blank");
		}
	}

	private LoadedData loadData() {
		Map<String, Station> loadedStations = new LinkedHashMap<>();
		Map<String, Map<String, List<SequencedStation>>> routesByLine = new LinkedHashMap<>();
		Map<String, LinkedHashSet<Edge>> loadedConnections = new LinkedHashMap<>();

		CsvMapper csvMapper = new CsvMapper();
		CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new ClassPathResource(STATIONS_RESOURCE).getInputStream(),
				StandardCharsets.UTF_8))) {
			reader.mark(1);
			if (reader.read() != '\uFEFF') {
				reader.reset();
			}
			try (MappingIterator<StationCsvRow> rows = csvMapper.readerFor(StationCsvRow.class)
					.with(csvSchema)
					.readValues(reader)) {
				List<StationCsvRow> csvRows = rows.readAll();
				List<String> csvColumns = new ArrayList<>(
						((CsvParser) rows.getParser()).getSchema().getColumnNames());
				if (csvColumns.isEmpty()) {
					throw new IOException("CSV is empty");
				}
				if (!csvColumns.equals(List.of(
						"Line Code", "Direction", "Station Code", "Station ID",
						"Chinese Name", "English Name", "Sequence"))) {
					throw new IOException("Unexpected CSV header");
				}

				int lineNumber = 1;
				for (StationCsvRow row : csvRows) {
					lineNumber++;
					if (row.isBlank()) {
						continue;
					}
					if (row.hasMissingValues()) {
						throw new IOException("Expected 7 non-empty columns at CSV row " + lineNumber);
					}

					Station station = new Station(
							Integer.toString(row.stationId()),
							row.stationCode(),
							row.chineseName(),
							row.englishName());
					Station existingStation = loadedStations.putIfAbsent(station.stationId(), station);
					if (existingStation != null && !existingStation.equals(station)) {
						throw new IOException("Station ID " + station.stationId()
								+ " has conflicting station details at CSV row " + lineNumber);
					}

					int sequence;
					try {
						sequence = BigDecimal.valueOf(row.sequence()).intValueExact();
					} catch (NumberFormatException | ArithmeticException exception) {
						throw new IOException("Invalid sequence at CSV row " + lineNumber, exception);
					}
					routesByLine
							.computeIfAbsent(row.lineCode(), ignored -> new LinkedHashMap<>())
							.computeIfAbsent(row.direction(), ignored -> new ArrayList<>())
							.add(new SequencedStation(sequence, station));
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to load MTR station data from " + STATIONS_RESOURCE, exception);
		}

		Map<String, LineResponse> loadedLines = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, List<SequencedStation>>> lineEntry : routesByLine.entrySet()) {
			Map<String, Station> uniqueStations = new LinkedHashMap<>();
			List<DirectionStations> directions = new ArrayList<>();
			for (Map.Entry<String, List<SequencedStation>> directionEntry : lineEntry.getValue().entrySet()) {
				List<SequencedStation> sequence = directionEntry.getValue();
				sequence.sort(Comparator.comparingInt(SequencedStation::sequence));
				List<Station> directionStations = sequence.stream()
						.map(SequencedStation::station)
						.toList();
				directionStations.forEach(station -> uniqueStations.putIfAbsent(station.stationId(), station));
				directions.add(new DirectionStations(directionEntry.getKey(), directionStations));

				for (int index = 1; index < sequence.size(); index++) {
					String previousId = sequence.get(index - 1).station().stationId();
					String stationId = sequence.get(index).station().stationId();
					addConnection(loadedConnections, previousId, stationId, lineEntry.getKey());
					addConnection(loadedConnections, stationId, previousId, lineEntry.getKey());
				}
			}
			loadedLines.put(
					lineEntry.getKey(),
					new LineResponse(lineEntry.getKey(), List.copyOf(uniqueStations.values()), directions));
		}

		Map<String, List<Edge>> immutableConnections = new LinkedHashMap<>();
		loadedConnections.forEach((stationId, edges) -> immutableConnections.put(stationId, List.copyOf(edges)));
		return new LoadedData(
				Map.copyOf(loadedStations),
				Map.copyOf(loadedLines),
				Map.copyOf(immutableConnections));
	}

	private void addConnection(
			Map<String, LinkedHashSet<Edge>> loadedConnections,
			String stationId,
			String neighborId,
			String lineCode) {
		loadedConnections.computeIfAbsent(stationId, ignored -> new LinkedHashSet<>())
				.add(new Edge(neighborId, lineCode));
	}

	public record Station(String stationId, String stationCode, String chineseName, String englishName) {
	}

	public record DirectionStations(String direction, List<Station> stations) {
		public DirectionStations {
			stations = List.copyOf(stations);
		}
	}

	public record LineResponse(String line, List<Station> stations, List<DirectionStations> directions) {
		public LineResponse {
			stations = List.copyOf(stations);
			directions = List.copyOf(directions);
		}
	}

	public record RouteLeg(String line, List<Station> stations) {
		public RouteLeg {
			stations = List.copyOf(stations);
		}
	}

	public record RouteResponse(
			String startStationId,
			String destinationStationId,
			int stopCount,
			List<Station> stations,
			List<RouteLeg> legs,
            FareTable.FareResponse fare) {
		public RouteResponse {
			stations = List.copyOf(stations);
			legs = List.copyOf(legs);
		}
	}

	private record Edge(String stationId, String lineCode) {
	}

	private record RouteState(String stationId, String lineCode) {
	}

	private record RouteSearchNode(
			RouteState state, int transfers, int stops, long order, RouteSearchNode previous) {
	}

	private record SequencedStation(int sequence, Station station) {
	}

	private record StationCsvRow(
			@JsonProperty("Line Code") String lineCode,
			@JsonProperty("Direction") String direction,
			@JsonProperty("Station Code") String stationCode,
			@JsonProperty("Station ID") Integer stationId,
			@JsonProperty("Chinese Name") String chineseName,
			@JsonProperty("English Name") String englishName,
			@JsonProperty("Sequence") Double sequence) {
		private boolean isBlank() {
			return isBlank(lineCode) && isBlank(direction) && isBlank(stationCode)
					&& isBlank(chineseName) && isBlank(englishName)
					&& stationId == null && sequence == null;
		}

		private boolean hasMissingValues() {
			return isBlank(lineCode) || isBlank(direction) || isBlank(stationCode)
					|| isBlank(chineseName) || isBlank(englishName) || stationId == null || sequence == null;
		}

		private boolean isBlank(String value) {
			return value == null || value.isBlank();
		}
	}

	private record LoadedData(
			Map<String, Station> stations,
			Map<String, LineResponse> lines,
			Map<String, List<Edge>> connections) {
	}
}
