package com.example.fare;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class McpTools {
    private final DataService dataService;

    public McpTools(DataService dataService) {
        this.dataService = dataService;
    }

    @Tool(name = "search_stations", description = "Search MTR stations by English or Chinese name or station code. "
            + "Use the returned stationId for get_route. If multiple stations could match, ask the user to choose.")
    public List<DataService.Station> searchStations(
            @ToolParam(description = "Full or partial station name or code; must not be blank") String query) {
        return dataService.searchStations(query);
    }

    @Tool(name = "get_route", description = "Find an MTR route by station IDs. Minimize line changes first, "
            + "then stops. Fare amounts are in HK cents and may be unavailable. Resolve station names with "
            + "search_stations first; ask the user for missing or ambiguous stations.")
    public DataService.RouteResponse getRoute(
            @ToolParam(description = "Starting station ID returned by search_stations") String start,
            @ToolParam(description = "Destination station ID returned by search_stations") String dest) {
        return dataService.findRoute(start, dest);
    }
}
